package bg.softuni.garage.parts;

import bg.softuni.garage.common.exception.BusinessRuleException;
import bg.softuni.garage.common.exception.ResourceNotFoundException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PartsErrorDecoderTest {

    private final PartsErrorDecoder decoder = new PartsErrorDecoder();

    @Test
    void aNotFoundBecomesAMissingResource() {
        Exception decoded = decoder.decode("reserve",
                response(404, "{\"detail\":\"No part with SKU NOPE\"}"));

        assertThat(decoded).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No part with SKU NOPE");
    }

    @Test
    void aConflictBecomesABusinessRuleFailure() {
        Exception decoded = decoder.decode("reserve",
                response(409, "{\"detail\":\"Only 2 unit(s) of BRK-1 are available\"}"));

        assertThat(decoded).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only 2 unit(s)");
    }

    @Test
    void aBadRequestBecomesABusinessRuleFailure() {
        Exception decoded = decoder.decode("reserve",
                response(400, "{\"detail\":\"One or more fields are invalid\"}"));

        assertThat(decoded).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anAuthorisationFailureIsReportedInPlainLanguage() {
        Exception decoded = decoder.decode("restock", response(403, "{\"detail\":\"denied\"}"));

        assertThat(decoded).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not authorised");
    }

    @Test
    void aBodyWithoutDetailFallsBackToAGenericMessage() {
        Exception decoded = decoder.decode("reserve", response(409, "{\"title\":\"Conflict\"}"));

        assertThat(decoded).hasMessageContaining("rejected the request");
    }

    @Test
    void anUnparseableBodyFallsBackToAGenericMessage() {
        Exception decoded = decoder.decode("reserve", response(409, "not json at all"));

        assertThat(decoded).hasMessageContaining("rejected the request");
    }

    @Test
    void anEmptyBodyFallsBackToAGenericMessage() {
        Exception decoded = decoder.decode("reserve", response(409, null));

        assertThat(decoded).hasMessageContaining("rejected the request");
    }

    @Test
    void anUnhandledStatusFallsThroughToTheDefaultDecoder() {
        Exception decoded = decoder.decode("reserve", response(500, "{\"detail\":\"boom\"}"));

        assertThat(decoded).isNotInstanceOf(BusinessRuleException.class);
    }

    private Response response(int status, String body) {
        Request request = Request.create(Request.HttpMethod.POST, "/api/reservations", Map.of(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());

        Response.Builder builder = Response.builder().status(status).request(request);
        if (body != null) {
            builder.body(body, StandardCharsets.UTF_8);
        }
        return builder.build();
    }
}
