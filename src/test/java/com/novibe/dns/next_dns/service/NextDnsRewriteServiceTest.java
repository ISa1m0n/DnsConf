package com.novibe.dns.next_dns.service;

import com.novibe.common.base_structures.BypassRoute;
import com.novibe.common.service.ExcludeRedirectCheckService;
import com.novibe.dns.next_dns.http.NextDnsRewriteClient;
import com.novibe.dns.next_dns.http.dto.request.CreateRewriteDto;
import com.novibe.dns.next_dns.http.dto.response.rewrite.RewriteDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void removesWwwRewriteThatCollapsedIntoApex() {
        NextDnsRewriteClient client = mock(NextDnsRewriteClient.class);
        when(client.fetchRewrites()).thenReturn(List.of(new RewriteDto("id1", "www.last.fm", "5.6.7.8")));
        NextDnsRewriteService service = new NextDnsRewriteService(client, mock(ExcludeRedirectCheckService.class));

        List<CreateRewriteDto> toCreate = service.cleanupOutdatedAndExcluded(
                service.buildNewRewrites(List.of(route("1.2.3.4", "last.fm"))));

        assertEquals(1, toCreate.size());
        assertEquals("last.fm", toCreate.get(0).name());
        assertEquals("1.2.3.4", toCreate.get(0).content());
        verify(client).deleteRewritesByIds(List.of("id1"));
    }

    @Test
    void keepsRewritesForDomainsThatAreNotInTheList() {
        NextDnsRewriteClient client = mock(NextDnsRewriteClient.class);
        when(client.fetchRewrites()).thenReturn(List.of(new RewriteDto("id2", "spotify.com", "9.9.9.9")));
        NextDnsRewriteService service = new NextDnsRewriteService(client, mock(ExcludeRedirectCheckService.class));

        List<CreateRewriteDto> toCreate = service.cleanupOutdatedAndExcluded(
                service.buildNewRewrites(List.of(route("1.2.3.4", "last.fm"))));

        assertEquals(1, toCreate.size());
        assertEquals("last.fm", toCreate.get(0).name());
        verify(client, never()).deleteRewritesByIds(anyList());
    }

    private BypassRoute route(String ip, String website) {
        return new BypassRoute(ip, website);
    }
}
