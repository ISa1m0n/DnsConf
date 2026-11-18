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
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
public class NextDnsRewriteService {

    private final NextDnsRewriteClient nextDnsRewriteClient;
    private final ExcludeRedirectCheckService excludeRedirectCheckService;

    public Map<String, CreateRewriteDto> buildNewRewrites(List<BypassRoute> overrides) {
        Map<String, CreateRewriteDto> rewriteDtos = new HashMap<>();
        overrides.forEach(route -> rewriteDtos.putIfAbsent(route.website(), new CreateRewriteDto(route.website(), route.ip())));
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

        for (RewriteDto existingRewrite : existingRewrites) {
            String domain = existingRewrite.name();
            String oldIp = existingRewrite.content();
            if (excludeRedirectCheckService.shouldExclude(domain)) {
                ignoredIds.add(existingRewrite.id());
                newRewriteRequests.remove(domain);
                continue;
            }
            CreateRewriteDto request = newRewriteRequests.get(domain);
            if (nonNull(request) && !request.content().equals(oldIp)) {
                outdatedIds.add(existingRewrite.id());
            } else {
                newRewriteRequests.remove(domain);
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
