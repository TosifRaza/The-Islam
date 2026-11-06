package app.ikp.backend.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public app version and in-app update information endpoint.
 * Android client queries this on startup to detect updates and prompt the user.
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppVersionController {

    @Value("${app.version.latest-code:9}")
    private int latestVersionCode;

    @Value("${app.version.latest-name:0.5.0-m5-fix6}")
    private String latestVersionName;

    @Value("${app.version.min-supported-code:1}")
    private int minSupportedCode;

    @Value("${app.version.apk-url:https://github.com/TosifRaza/The-Islam/releases/latest/download/The-Islam.apk}")
    private String apkUrl;

    @Value("${app.version.release-notes:Initial release with full Quran, Hadith, and Islamic content.}")
    private String releaseNotes;

    @Value("${app.version.force-update:false}")
    private boolean forceUpdate;

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> getVersionInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("latestVersionCode", latestVersionCode);
        data.put("latestVersionName", latestVersionName);
        data.put("minSupportedVersionCode", minSupportedCode);
        data.put("apkUrl", apkUrl);
        data.put("releaseNotes", releaseNotes);
        data.put("forceUpdate", forceUpdate);

        return ResponseEntity.ok(Map.of("data", data));
    }
}
