package com.novibe.dns.next_dns.service;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.common.util.Log;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.novibe.common.config.EnvironmentVariables.ALLOW_SHRINK;
import static com.novibe.common.util.DataParser.removeWWW;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteService {

    private final NextDnsRewriteClient nextDnsRewriteClient;
    private final ExcludeRedirectCheckService excludeRedirectCheckService;

    public Map<String, CreateRewriteDto> buildNewRewrites(List<BypassRoute> overrides) {
        // Rewrite в NextDNS действует и на поддомены, поэтому www.X при том же адресе,
        // что у X, лишний: его и так закрывает X. Но если адреса различаются (разнос
        // www и apex, как у last.fm и deezer), www надо оставить отдельной записью:
        // иначе www уедет на адрес apex, Firefox склеит соединения и сайт ответит 403.
        Map<String, String> apexIps = new HashMap<>();
        overrides.stream()
                .filter(route -> route.website().equals(removeWWW(route.website())))
                .forEach(route -> apexIps.putIfAbsent(route.website(), route.ip()));

        Map<String, CreateRewriteDto> rewriteDtos = new HashMap<>();
        for (BypassRoute route : overrides) {
            String domain = route.website();
            String apex = removeWWW(domain);
            boolean coveredByApex = !domain.equals(apex) && route.ip().equals(apexIps.get(apex));
            String name = coveredByApex ? apex : domain;
            rewriteDtos.putIfAbsent(name, new CreateRewriteDto(name, route.ip()));
        }
        return rewriteDtos;
    }

    public List<CreateRewriteDto> cleanupOutdatedAndExcluded(Map<String, CreateRewriteDto> newRewriteRequests) {
        List<RewriteDto> existingRewrites = getExistingRewrites();

        // Форк: если новый список внезапно стал вдвое короче, это почти всегда битый
        // или недокачанный hosts, а не осознанное сокращение. Профиль не трогаем.
        if (!ALLOW_SHRINK && !existingRewrites.isEmpty()
                && newRewriteRequests.size() * 2 < existingRewrites.size()) {
            Log.fail("Refusing to apply %s rewrites while %s are already configured: the new list looks broken (set ALLOW_SHRINK=1 to override)"
                    .formatted(newRewriteRequests.size(), existingRewrites.size()));
            return List.of();
        }

        List<String> outdatedIds = new ArrayList<>();
        List<String> ignoredIds = new ArrayList<>();
        List<String> collapsedIds = new ArrayList<>();

        // Снимок нужного набора: в цикле из newRewriteRequests удаляются уже совпавшие
        // записи, а сверяться надо с исходным списком.
        Map<String, CreateRewriteDto> desired = Map.copyOf(newRewriteRequests);

        for (RewriteDto existingRewrite : existingRewrites) {
            String domain = existingRewrite.name();
            String oldIp = existingRewrite.content();
            if (excludeRedirectCheckService.shouldExclude(domain)) {
                ignoredIds.add(existingRewrite.id());
                newRewriteRequests.remove(domain);
                continue;
            }
            CreateRewriteDto request = desired.get(domain);
            if (nonNull(request)) {
                if (!request.content().equals(oldIp)) {
                    outdatedIds.add(existingRewrite.id());
                } else {
                    newRewriteRequests.remove(domain);
                }
            } else if (!domain.equals(removeWWW(domain)) && desired.containsKey(removeWWW(domain))) {
                // www.X схлопнулся в apex X: rewrite и так действует на поддомены, а старая
                // отдельная запись с другим IP остаётся и мешает. Так 23.11.2026 www.last.fm
                // держал мёртвый адрес и сайт не открывался, хотя apex уже был живой.
                collapsedIds.add(existingRewrite.id());
            }
        }
        newRewriteRequests.keySet().removeIf(excludeRedirectCheckService::shouldExclude);

        if (!outdatedIds.isEmpty()) {
            Log.io("Removing %s outdated rewrites from NextDNS".formatted(outdatedIds.size()));
            nextDnsRewriteClient.deleteRewritesByIds(outdatedIds);
        }
        if (!ignoredIds.isEmpty()) {
            Log.io("Removing %s excluded rewrites from NextDNS".formatted(ignoredIds.size()));
            nextDnsRewriteClient.deleteRewritesByIds(ignoredIds);
        }
        if (!collapsedIds.isEmpty()) {
            Log.io("Removing %s rewrites already covered by their apex domain".formatted(collapsedIds.size()));
            nextDnsRewriteClient.deleteRewritesByIds(collapsedIds);
        }
        return List.copyOf(newRewriteRequests.values());
    }

    public List<RewriteDto> getExistingRewrites() {
        Log.io("Fetching existing rewrites from NextDNS");
        return nextDnsRewriteClient.fetchRewrites();
    }

    public void saveRewrites(List<CreateRewriteDto> createRewriteDtos) {
        Log.io("Saving %s new rewrites to NextDNS...".formatted(createRewriteDtos.size()));
        nextDnsRewriteClient.saveRewrites(createRewriteDtos);
    }

    public void removeAll() {
        Log.io("Fetching existing rewrites from NextDNS");
        List<RewriteDto> list = nextDnsRewriteClient.fetchRewrites();
        List<String> ids = list.stream().map(RewriteDto::id).toList();
        Log.io("Removing rewrites from NextDNS");
        nextDnsRewriteClient.deleteRewritesByIds(ids);
    }

}
