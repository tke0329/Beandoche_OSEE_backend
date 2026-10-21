package com.beandoche_osee_backend.domain.auth.provider.kakao;

import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoUnlinkClient {
    public enum Outcome { COMPLETED, CONFIG_REQUIRED, RETRY }
    private final String adminKey;
    private final RestClient client;

    @org.springframework.beans.factory.annotation.Autowired
    public KakaoUnlinkClient(@Value("${auth.kakao.admin-key:}") String adminKey) {
        this(adminKey, createClient());
    }

    KakaoUnlinkClient(String adminKey, RestClient client) {
        this.adminKey = adminKey;
        this.client = client;
    }

    private static RestClient createClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl("https://kapi.kakao.com").requestFactory(factory).build();
    }

    public Outcome unlink(String providerUserId) {
        if (adminKey == null || adminKey.isBlank() || adminKey.startsWith("replace-")) return Outcome.CONFIG_REQUIRED;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);
        try {
            Map<?, ?> result = client.post().uri("/v1/user/unlink").header("Authorization", "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            return result != null && providerUserId.equals(String.valueOf(result.get("id"))) ? Outcome.COMPLETED : Outcome.RETRY;
        } catch (HttpClientErrorException failure) {
            return failure.getStatusCode().value() == 401 || failure.getStatusCode().value() == 403 ? Outcome.CONFIG_REQUIRED : Outcome.RETRY;
        } catch (RestClientException failure) {
            return Outcome.RETRY;
        }
    }
}
