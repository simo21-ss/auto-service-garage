package bg.softuni.garage.parts;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class PartsErrorDecoder implements ErrorDecoder {

    private static final String DETAIL_FIELD = "detail";
    private static final String FALLBACK_MESSAGE = "The parts service rejected the request";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus status = HttpStatus.resolve(response.status());
        byte[] body = readBody(response);
        String detail = extractDetail(body);

        log.warn("Parts service returned {} for {}: {}", response.status(), methodKey, detail);

        if (status == HttpStatus.NOT_FOUND) {
            return new ResourceNotFoundException(detail);
        }
        if (status == HttpStatus.CONFLICT || status == HttpStatus.BAD_REQUEST) {
            return new BusinessRuleException(detail);
        }
        if (status == HttpStatus.FORBIDDEN || status == HttpStatus.UNAUTHORIZED) {
            return new BusinessRuleException("The workshop is not authorised to change parts stock");
        }
        return defaultDecoder.decode(methodKey, response.toBuilder().body(body).build());
    }

    private byte[] readBody(Response response) {
        if (response.body() == null) {
            return new byte[0];
        }

        try (InputStream body = response.body().asInputStream()) {
            return body.readAllBytes();
        } catch (IOException exception) {
            log.warn("Could not read the parts service error body: {}", exception.getMessage());
            return new byte[0];
        }
    }

    private String extractDetail(byte[] body) {
        if (body.length == 0) {
            return FALLBACK_MESSAGE;
        }

        try {
            JsonNode detail = objectMapper.readTree(body).get(DETAIL_FIELD);
            return detail == null || detail.asText().isBlank() ? FALLBACK_MESSAGE : detail.asText();
        } catch (IOException exception) {
            return FALLBACK_MESSAGE;
        }
    }
}
