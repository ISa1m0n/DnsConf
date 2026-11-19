package com.novibe.dns.next_dns.service;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class NextDnsRewriteServiceTest {

    private final NextDnsRewriteService service = new NextDnsRewriteService(mock(), mock());

    @Test
    void dropsWwwWhenItPointsToTheSameIpAsApex() {
        Map<String, CreateRewriteDto> rewrites = service.buildNewRewrites(List.of(
                route("1.2.3.4", "www.openai.com"),
                route("1.2.3.4", "openai.com")));

        assertEquals(Set.of("openai.com"), rewrites.keySet());
        assertEquals("1.2.3.4", rewrites.get("openai.com").content());
    }

    @Test
    void keepsWwwWhenItPointsToAnotherIpThanApex() {
        Map<String, CreateRewriteDto> rewrites = service.buildNewRewrites(List.of(
                route("1.2.3.4", "www.last.fm"),
                route("5.6.7.8", "last.fm")));

        assertEquals(Set.of("www.last.fm", "last.fm"), rewrites.keySet());
        assertEquals("1.2.3.4", rewrites.get("www.last.fm").content());
        assertEquals("5.6.7.8", rewrites.get("last.fm").content());
    }

    @Test
    void keepsDomainWithoutWwwAsIs() {
        Map<String, CreateRewriteDto> rewrites = service.buildNewRewrites(List.of(
                route("1.2.3.4", "claude.ai")));

        assertEquals(Set.of("claude.ai"), rewrites.keySet());
    }

    @Test
    void keepsWwwWhenApexIsNotListed() {
        Map<String, CreateRewriteDto> rewrites = service.buildNewRewrites(List.of(
                route("1.2.3.4", "www.example.com")));

        assertEquals(Set.of("www.example.com"), rewrites.keySet());
        assertEquals("1.2.3.4", rewrites.get("www.example.com").content());
    }

    private BypassRoute route(String ip, String website) {
        return new BypassRoute(ip, website);
    }
}
