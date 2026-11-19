package com.example.spamer.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.ConflictException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.NotFoundException;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeignErrorTranslatorTest {

    private static FeignException status(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("x", feign.Response.builder()
                .status(status)
                .reason("r")
                .request(request)
                .headers(Map.of())
                .body("{}", StandardCharsets.UTF_8)
                .build());
    }

    @Test
    void clientErrorsKeepTheirMeaning() {
        assertThat(FeignErrorTranslator.translate("s", status(404)))
                .isInstanceOf(NotFoundException.class);
        assertThat(FeignErrorTranslator.translate("s", status(409)))
                .isInstanceOf(ConflictException.class);
        assertThat(FeignErrorTranslator.translate("s", status(422)))
                .isInstanceOf(BusinessException.class);
        assertThat(FeignErrorTranslator.translate("s", status(400)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void wrappedClientErrorIsFound() {
        assertThat(FeignErrorTranslator.translate("s", new RuntimeException(status(404))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void everythingElseMeansUnavailable() {
        assertThat(FeignErrorTranslator.translate("s", status(500)))
                .isInstanceOf(DependencyUnavailableException.class);
        assertThat(FeignErrorTranslator.translate("s", new IllegalStateException("open")))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
