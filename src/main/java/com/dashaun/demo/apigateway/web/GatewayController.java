package com.dashaun.demo.apigateway.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@RestController
public class GatewayController {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("h:mma 'on' MMMM d, yyyy", Locale.US);

    private final WebClient discoveryClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> serviceNames;
    private final ZoneId zone;

    public GatewayController(@LoadBalanced WebClient.Builder webClientBuilder,
                             @Value("${gateway.services}") String services,
                             @Value("${demo.timezone:America/Chicago}") String displayZone) {
        this.discoveryClient = webClientBuilder.build();
        this.serviceNames = Arrays.asList(services.split(","));
        this.zone = ZoneId.of(displayZone);
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<String> landingPage() {
        List<Mono<String[]>> rows = new ArrayList<>();
        for (String name : serviceNames) {
            rows.add(fetchInfo(name.trim()));
        }
        return Flux.fromIterable(rows)
                .flatMap(row -> row)
                .collectSortedList(Comparator.comparing(row -> row[0]))
                .map(this::render);
    }

    private Mono<String[]> fetchInfo(String name) {
        return discoveryClient.get()
                .uri("lb://" + name + "/actuator/info")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .map(body -> parseInfo(name, body))
                .onErrorReturn(new String[]{name, "DOWN", "-", "-", "-", "-"});
    }

    private String[] parseInfo(String name, String body) {
        try {
            JsonNode info = objectMapper.readTree(body);
            String uptimeSeconds = info.at("/startup/uptimeSeconds").asText("-");
            return new String[]{
                    name,
                    "UP",
                    info.at("/spring/boot/version").asText("unknown"),
                    info.at("/java/version").asText("unknown"),
                    info.at("/startup/startedAt").asText("unknown"),
                    uptimeSeconds.equals("-") ? "-" : uptimeSeconds + "s"};
        } catch (Exception e) {
            return new String[]{"unknown", "DOWN", "-", "-", "-", "-"};
        }
    }

    private String render(List<String[]> rows) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
        html.append("<meta http-equiv=\"refresh\" content=\"10\">");
        html.append("<title>Insurance Platform</title><style>");
        html.append("body{font-family:-apple-system,Helvetica,Arial,sans-serif;background:#111827;color:#e5e7eb;margin:0;padding:40px;}");
        html.append("h1{font-weight:600;margin:0 0 8px;} p.sub{color:#9ca3af;margin:0 0 32px;}");
        html.append("table{border-collapse:collapse;width:100%;max-width:1040px;}");
        html.append("th{color:#9ca3af;text-transform:uppercase;font-size:12px;letter-spacing:.08em;text-align:left;padding:10px 16px;border-bottom:1px solid #374151;}");
        html.append("td{padding:14px 16px;border-bottom:1px solid #1f2937;}");
        html.append("code{background:#1f2937;padding:2px 8px;border-radius:6px;font-size:13px;}");
        html.append(".up{color:#4ade80;font-weight:600;} .down{color:#f87171;font-weight:600;}");
        html.append(".footer{color:#6b7280;font-size:13px;margin-top:24px;}</style></head><body>");
        html.append("<h1>Insurance Platform</h1>");
        html.append("<p class=\"sub\">Service status discovered through Eureka. This page refreshes every 10 seconds.</p>");
        html.append("<table><tr><th>Service</th><th>Status</th><th>Spring Boot</th><th>Java</th><th>Started At</th><th>Uptime</th></tr>");
        for (String[] row : rows) {
            boolean up = "UP".equals(row[1]);
            html.append("<tr><td><code>").append(escape(row[0])).append("</code></td>");
            html.append("<td class=\"").append(up ? "up" : "down").append("\">").append(escape(row[1])).append("</td>");
            html.append("<td>").append(escape(row[2])).append("</td>");
            html.append("<td>").append(escape(row[3])).append("</td>");
            html.append("<td>").append(escape(row[4])).append("</td>");
            html.append("<td>").append(escape(row[5])).append("</td></tr>");
        }
        html.append("</table>");
        html.append("<p class=\"footer\">Gateway refreshed at ")
                .append(DISPLAY_FORMAT.format(ZonedDateTime.now(zone))).append(" (local demo time)</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
