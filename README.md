> [!NOTE]
> **`nextdns-sync`** - personal fork of [DnsConf](https://github.com/noVibe/DnsConf). On top of upstream it adds safety guards (see [Fork changes](#fork-changes))
> and is wired to one specific setup:
>
> - `REDIRECT` points to the raw `hosts-ai.txt` from
>   [ai-hosts](https://github.com/ISa1m0n/ai-hosts), `BLOCK` - to an ad list
>   (`hagezi-multi-normal`);
> - the job runs hourly and right after the probe on the PC commits new addresses
>   (`repository_dispatch: geohide-updated`);
> - `CLIENT_ID` and `AUTH_SECRET` live in repository Secrets, never in code.
>
> Full chain: probe on the PC -> `health.txt` -> GitHub Actions build -> `hosts-ai.txt`
> -> this job -> rewrites in NextDNS.

🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿 **English**](README.md) | [**Русский**](README.ru.md)

[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>

# DNS Block&Redirect Configurer

**Configure Redirect and Block rules for Cloudflare and NextDNS accounts.**

**Ready-to-run via GitHub Actions.** [Video guide](https://www.youtube.com/watch?v=vbAXM_xAL5I)

## Comparison of Free Plans: NextDNS vs Cloudflare

|                                  | NextDNS                                                         | Cloudflare                                                                                                           |
|----------------------------------|-----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| **DNS Query Limit**              | 300,000 per month                                               | 100,000 per day                                                                                                      |
| **Billing information required** | No                                                              | Yes                                                                                                                  |
| **IPv4 Restrictions**            | DNS queries are limited to a single IP address (can be changed) | DNS queries are strictly limited to a single IP address (automatically assigned by Cloudflare and cannot be changed) |
| **DoH / DoT / IPv6**             | Unlimited                                                       | Unlimited                                                                                                            |
| **Setup API Limits**             | 60 requests per minute                                          | Unlimited                                                                                                            |
| **General Limitations**          | None                                                            | Infrastructure is blocked by Roskomnadzor (availability issues in Russia)                                            |
| **Advantages**                   | Built-in ad and tracker blocking options                        | More reliable and fast infrastructure                                                                                |

In summary: 
- If you are located in Russia, **NextDNS** is your only viable option due to Roskomnadzor restrictions.
- If you are in other country than Russia, and you're ok with a credit card gate, **Cloudflare** offers more generous limits on the free plan. 
Tracker and ad blocking can also be enabled by providing a domain blocklist in `BLOCK`, for example: https://small.oisd.nl/domainswild2

## Easy Setup

Use the configurator https://dns-conf-ui.vercel.app in **Quick** mode. It will automatically configure your DNS profile
and perform all required GitHub setup steps.

You only need to sign in with GitHub and provide **CLIENT_ID** and **AUTH_SECRET**. More details on where to find these
values here: [Setup credentials](#setup-credentials)

## Standard Setup

[Set up credentials](#set-up-credentials)

[Set up profile](#set-up-profile)

[Set up data sources](#set-up-data-sources)

[Set up exclude redirects (optional)](#set-up-exclude-redirects-optional)

[Set up a DNS donor](#set-up-a-dns-donor)

[Multiple profiles setup](#multiple-profiles-setup)

[GitHub Actions setup](#github-actions-setup)

---

## Set up credentials

### NextDNS credentials setup

1) Generate an **API KEY** from https://my.nextdns.io/account and set it as an **environment variable** `AUTH_SECRET`

2) Click on **NextDNS** logo. On the opened page, copy ID from Endpoints section.
   Set it as **environment variable** `CLIENT_ID`

### Cloudflare credentials setup

1) After signing up into a **Cloudflare**, navigate to _Zero Trust_ tab and create an account.

2) Create a **Cloudflare API token**, from https://dash.cloudflare.com/profile/api-tokens

with 2 permissions:

    Account.Zero Trust : Edit

    Account.Account Firewall Access Rules : Edit

Set API token to **environment variable** `AUTH_SECRET`

3) Get your **Account ID** from : https://dash.cloudflare.com/?to=/:account/workers

Set **Account ID** to **environment variable** `CLIENT_ID`

---

## Set up profile

Set **environment variable** `DNS` with DNS provider name (**Cloudflare** or **NextDNS**)

---

## Set up data sources

Each data source must be a link to a hosts file,
e.g. https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

You can provide multiple sources split by coma:
https://first.com/hosts,https://second.com/hosts

### 1) Set up Redirects

Set sources to **environment variable** `REDIRECT`

Script will parse sources, filtering out redirects to `0.0.0.0` and `127.0.0.1`

Thus, parsing lines:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `1.2.3.4 domain.to.redirect` for the further redirect processing.

+ Redirect priority follows sources order. If domain appears more than one time, the first only IP will be applied.

### 2) Set up Blocklist

Set sources to **environment variable** `BLOCK`

Script will parse sources, keeping only redirects to `0.0.0.0`, `127.0.0.1`, `::1`, and also lines containing domain
only.

Thus, parsing lines

    1.2.3.4 domain.to.redirect
    0.0.0.0 domain.to.block
    127.0.0.1 another.to.block
    ::1 ipv6.to.block
    no-ip.just.domain

will keep only

    domain.to.block
    another.to.block
    no-ip.just.domain
    ipv6.to.block

for the further block processing.

+ You may want to provide the same source for both `BLOCK` and `REDIRECT` for **Cloudflare**.
+ For **NextDNS**, the best option might be to set `REDIRECT` only, and then manually choose any blocklists at the
  _Privacy_ tab.

---

## Set up exclude redirects (optional)

Put domains to **environment variable** `EXCLUDE_REDIRECT` separated by coma, e.g. `instagram.com,twitch.com`

These domains and their subdomains:

- will be removed from existing redirect rules;
- won't be added with new ones.

---

## Set up a DNS donor

If IP addresses of domains are outdated, they can be updated via "donor" DNS. You need:

1) Create **environment variable** `DONOR_DNS`
2) Provide the **DNS-provider** that will be used as a **donor**.
   Use one of the following formats:

- **IPv4** (e.g. `111.88.96.50`)
- **DoH** (e.g. `https://xbox-dns.ru/dns-query`)

For instance, if your hosts-file supplies following:

    1.2.3.4 domain-1.to.redirect
    1.2.3.4 domain-2.to.redirect
    1.2.3.4 domain-3.to.redirect

Then, a `DONOR_DNS` value will be called for each domain, fetching new IP addresses:

    5.6.7.8 domain-1.to.redirect
    2.3.4.5 domain-2.to.redirect
    9.8.7.6 domain-3.to.redirect

Thus, the new IPs will be used for the further redirect rules upload process.

---

## Set up redirects compression (optional, NextDNS only)

NextDNS applies a redirect rule to the domain and all its subdomains, so large hosts-files can be shrunk to fit the
rewrites limit. Set **environment variable** `COMPRESS` to `true` to enable it (any other value or no value keeps it off).

Before upload, the script drops:

- duplicate domains (the first IP of a domain is kept);
- subdomains of domains that are listed too.

For instance, hosts-file lines:

    1.2.3.4 google.com
    1.2.3.4 gemini.google.com
    1.2.3.4 a.example.com
    1.2.3.4 b.example.com

turn into:

    1.2.3.4 google.com
    1.2.3.4 a.example.com
    1.2.3.4 b.example.com

Note that a dropped subdomain gets the IP of its parent domain, even if the hosts-file set a different IP for it.

When compression is turned on and the existing NextDNS redirects are not compressed yet, all of them are removed and
uploaded again in compressed form.

---

## Multiple profiles setup

### Restrictions

All profiles get _similar_ settings. That means `BLOCK`, `REDIRECT` and `EXCLUDE_REDIRECT` are **shared**.

### Multiple profiles of single provider

Put your profiles separated by coma into related **environment variables**.
E.g., two NextDNS profiles must be set as shown:

- `AUTH_SECRET` has: `secret_NextDns_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_NextDns_2`

### Multiple profiles of different providers

In addition to setting above, list provider for each profile in **environment variable** `DNS`. For example:

- `DNS` has: `NEXTDNS,CLOUDFLARE,NEXTDNS`
- `AUTH_SECRET` has: `secret_NextDns_1,secret_Cloudflare_1,secret_NextDns_2`
- `CLIENT_ID` has `client_id_NextDns_1,client_id_Cloudflare_1,client_id_NextDns_2`

---

## Script Behavior

### Cloudflare

Previously generated data will be removed. Script recognizes old data by marks:

+ Name prefix for List: **_Blocked websites by script_** and **_Override websites by script_**
+ Name prefix for Rule: **_Rules set by script_**
+ Different **_Session id_**. **_Session id_** is stored in a description field.

After removing old data, new lists and rules will be generated and applied.

If you want to clear **Cloudflare** block/redirect settings, launch the script without providing sources in related *
*environment variables**. E.g. providing no value for **environment variable** `BLOCK` will cause removing old related
data: lists and rules used to set up blocks.

### NextDNS

For `REDIRECT`:

+ Existing domain will be updated if redirect IP has changed
+ If new domains are provided, they will be added
+ The rest redirect settings are kept untouched

For `BLOCK`:

+ If new domains are provided, they will be added
+ The rest block settings are kept untouched

Previously generated data is removed **ONLY** when both `BLOCK` and `REDIRECT` sources were not provided.

---

## GitHub Actions setup

#### Step-by-step video guide: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

#### Steps

1) Fork repository
2) Go _Settings_ => _Environments_
3) Create _New environment_ with name `DNS`
4) Provide `AUTH_SECRET` and `CLIENT_ID` to **Environment secrets**
5) Provide `DNS`,`REDIRECT`, `BLOCK` and `EXCLUDE_REDIRECT` to **Environment variables**. Optional `DONOR_DNS` and
   `COMPRESS` go there too

+ The action is executed **hourly** as a fallback, and immediately when the ai-hosts fork
  sends a `repository_dispatch` (`geohide-updated`). To set another time, change cron at
  `.github/workflows/github_action.yml`
+ You can run the action manually via `Run workflow` button: switch to _Actions_ tab and choose workflow named **DNS
  Block&Redirect Configurer cron task**
+ **The fork pulls upstream by itself.** `sync-upstream.yml` merges `noVibe/DnsConf` into
  `main` once a day, but only when the merge is clean: a conflict leaves `main` untouched and
  opens an issue. After the merge it checks the fork's markers, runs the tests, and only then
  pushes. The **Sync fork** button is no longer needed. Pushing requires
  _Settings_ => _Actions_ => _General_ => _Workflow permissions_ set to
  **Read and write permissions**.

---

## Keep the action running

GitHub disables scheduled workflows in a public repository
[if there has been no activity in it for 60 days](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).
Your fork is a public repository. **A scheduled run is not counted as activity**. Therefore, approximately once every two months
you need to run it manually.

**How to check:** Click the badge below (the same one is at the top of the README) to go to the run history.<br>
[![Last Build](../../actions/workflows/github_action.yml/badge.svg?branch=main)](../../actions/workflows/github_action.yml)<br>
A disabled workflow will have a notice that scheduled runs are turned off. In the run history (workflow runs), you can see the date of the last run.

**How to turn it on again:**
Click the badge above and press the Run Workflow button<br>
_Or_: _Actions_ tab → workflow **DNS Block&Redirect Configurer cron task** → **Enable workflow** button.<br>
Reconfiguring via the configurator https://dns-conf-ui.vercel.app also enables the workflow.

Keep in mind that proxy IPs change over time, so in a fork that has been disabled for a long time, the rules remain
outdated until the first successful run.

## Fork changes

On top of upstream this fork:

- **Never wipes the profile by accident.** When both `BLOCK` and `REDIRECT` are empty
  (a cleared repository variable, a broken environment), upstream removes every rule.
  Here settings are left untouched and the run logs a warning. Set `WIPE=1` to get the
  upstream behaviour back.
- **Refuses to apply a list that suddenly halved.** If the new set of rewrites is less
  than half of what is already configured in NextDNS, the profile is left as is: that is
  almost always a truncated or broken hosts file. Set `ALLOW_SHRINK=1` to override.
- **Runs hourly and on demand.** The cron is hourly as a fallback, and
  the `repository_dispatch` event `geohide-updated` starts the job right after
  the probe on the PC changed `health.txt` in the ai-hosts fork.
- **Queues instead of cancelling.** Two runs touching the same profile are serialized,
  so a run is never cut in the middle between deleting and creating rewrites.
- **Preserves `www.` when it points to a different IP than apex.** The parser no longer
  strips `www.` from override lists. If `www.example.com` and `example.com` resolve to
  different IPs (e.g. `www.last.fm` on MAIN, `last.fm` on ALT), both rewrites are kept.
  Otherwise Firefox coalesces HTTP/2 connections and the site returns 403.
- **Drops a leftover `www.` rewrite when the apex covers it.** If `www.example.com`
  resolves to the same IP as `example.com`, a separate `www.` rewrite is pointless: the
  apex rewrite already covers subdomains. The old record with another address is deleted,
  otherwise the domain keeps answering with a dead IP while the apex is already fixed.
  That is what happened to `www.last.fm` on 23.11.2026.
- **`COMPRESS` conflicts with a split `www`.** Compression drops a subdomain when its
  parent is listed too, so with `COMPRESS=true` the `www.last.fm` record with its own
  address disappears and the domain falls back to the apex address. Compression is off by
  default, so the current setup is unaffected, but it must not be turned on while `www`
  and apex point to different servers.
- **Tests on every push to `main`.** `tests.yml` runs on pull requests and on pushes,
  so a Java change cannot slip in unchecked.
- **Automatic upstream sync.** `sync-upstream.yml` merges `noVibe/DnsConf` into `main`
  once a day, but only when the merge is clean. It then checks the fork's markers (the
  split `www` handling, `WIPE`, `ALLOW_SHRINK`, the `geohide-updated` dispatch, queue
  instead of cancel), runs the tests, and only then pushes. A conflict leaves `main`
  untouched and opens an issue. The Sync fork button is no longer needed.

### How to check that it works

1. **Actions.** Open the **DNS Block&Redirect Configurer cron task** workflow and confirm
   the last run is green. The log goes `Fetching existing rewrites`, `Removing N ...`,
   `Saving N new rewrites`.
2. **DNS on the PC.** `nslookup chatgpt.com` returns the address from `health.txt`,
   `nslookup gemini.google.com` returns `GEMINI`.
3. **One domain is broken while the rest work.** Check the NextDNS profile for a separate
   `www.` or subdomain record with another address: it overrides the apex, and the next
   run removes it.
