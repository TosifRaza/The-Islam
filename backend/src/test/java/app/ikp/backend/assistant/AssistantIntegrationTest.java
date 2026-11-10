package app.ikp.backend.assistant;

import app.ikp.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"unchecked", "rawtypes"})
public class AssistantIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("Assistant suggestions endpoint returns curated research questions")
    void testSuggestions() {
        ResponseEntity<Map> res = rest.getForEntity("/api/v1/assistant/suggestions", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        Map<?, ?> body = res.getBody();
        assert body != null;
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        assertThat(data).isNotEmpty();
        assertThat(data.size()).isGreaterThanOrEqualTo(5);
        assertThat(data.get(0).get("question")).toString().contains("Ayat al-Kursi");
    }

    @Test
    @DisplayName("Assistant correctly answers Ayat al-Kursi query with verified Quran and Hadith citations")
    void testAyatAlKursiQuery() {
        AssistantDtos.AssistantQueryRequest req = new AssistantDtos.AssistantQueryRequest(
                "What is the meaning and virtue of Ayat al-Kursi?",
                "conv-test-1",
                "en"
        );
        ResponseEntity<Map> res = rest.postForEntity("/api/v1/assistant/query", req, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        Map<?, ?> body = res.getBody();
        assert body != null;
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("groundedStatus")).isEqualTo("FULLY_GROUNDED");
        assertThat(data.get("title").toString()).contains("Ayat al-Kursi");
        String answer = data.get("answer").toString();
        assertThat(answer).contains("Surah Al-Baqarah, 2:255");
        assertThat(answer).contains("Al-Hayy al-Qayyum");

        List<Map<String, Object>> citations = (List<Map<String, Object>>) data.get("citations");
        assertThat(citations).isNotEmpty();
        assertThat(citations.stream().anyMatch(c -> "quran".equals(c.get("type")))).isTrue();
        assertThat(citations.stream().anyMatch(c -> "article".equals(c.get("type")))).isTrue();

        List<String> followUps = (List<String>) data.get("followUps");
        assertThat(followUps).isNotEmpty();
    }

    @Test
    @DisplayName("Assistant correctly answers Salah conditions query with Shuroot and Arkan")
    void testSalahQuery() {
        AssistantDtos.AssistantQueryRequest req = new AssistantDtos.AssistantQueryRequest(
                "What are the essential conditions and pillars of Salah?",
                "conv-test-2",
                "en"
        );
        ResponseEntity<Map> res = rest.postForEntity("/api/v1/assistant/query", req, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        Map<?, ?> body = res.getBody();
        assert body != null;
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("groundedStatus")).isEqualTo("FULLY_GROUNDED");
        String answer = data.get("answer").toString();
        assertThat(answer).contains("The Nine Preconditions (Shuroot as-Salah)");
        assertThat(answer).contains("The Fourteen Integral Pillars (Arkan as-Salah)");

        List<Map<String, Object>> citations = (List<Map<String, Object>>) data.get("citations");
        assertThat(citations).isNotEmpty();
        assertThat(citations.stream().anyMatch(c -> "fatwa".equals(c.get("type")))).isTrue();
    }

    @Test
    @DisplayName("Assistant safely returns INSUFFICIENT_DATA and alternative research for unapproved topics")
    void testInsufficientData() {
        AssistantDtos.AssistantQueryRequest req = new AssistantDtos.AssistantQueryRequest(
                "How do I manufacture a rocket engine?",
                "conv-test-3",
                "en"
        );
        ResponseEntity<Map> res = rest.postForEntity("/api/v1/assistant/query", req, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        Map<?, ?> body = res.getBody();
        assert body != null;
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertThat(data.get("groundedStatus")).isEqualTo("INSUFFICIENT_DATA");
        String answer = data.get("answer").toString();
        assertThat(answer).contains("I couldn't find sufficient information about this topic in the approved IKP research library");
        assertThat(answer).contains("M6 Safety Protocol");

        List<Map<String, Object>> citations = (List<Map<String, Object>>) data.get("citations");
        assertThat(citations).isNotEmpty();
    }

    @Test
    @DisplayName("Assistant feedback is recorded and reflected in overview")
    void testFeedbackAndOverview() {
        AssistantDtos.AssistantFeedbackRequest fb = new AssistantDtos.AssistantFeedbackRequest(
                "ans-123",
                "What is the meaning of Ayat al-Kursi?",
                "positive",
                "Excellent academic depth and verified citations."
        );
        ResponseEntity<Map> resFb = rest.postForEntity("/api/v1/assistant/feedback", fb, Map.class);
        assertThat(resFb.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> resOverview = rest.getForEntity("/api/v1/assistant/overview", Map.class);
        assertThat(resOverview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resOverview.getBody()).isNotNull();
        Map<?, ?> body = resOverview.getBody();
        assert body != null;
        Map<String, Object> overview = (Map<String, Object>) body.get("data");
        assertThat(overview.get("groundingEngineStatus")).isEqualTo("M6_ACTIVE");
        assertThat(((Number) overview.get("totalFeedbackRecorded")).intValue()).isGreaterThanOrEqualTo(1);
    }
}
