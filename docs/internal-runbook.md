# Internal runbook — host operations

> **Internal only.** This document describes how Model Radar is operated on the
> current INTICS host (a Windows laptop running WSL2) and includes the address of the
> demo link. Remove or redact it before sharing the repository outside INTICS. The
> [main README](../README.md) is the version intended for external readers.

Everything here concerns *this deployment*, not the application itself. Nothing in it
is required to build or run Model Radar elsewhere — see
[Deployment](../README.md#deployment) for that.

---

## 1. Layout

| Path | What it is |
| --- | --- |
| `~/projects/intics-model-radar` | The repository |
| `~/model-radar-run/` | Runtime directory, deliberately **outside** the repo |
| `backend/target/model-radar-0.0.1-SNAPSHOT.jar` | Production JAR (~107 MB, Vaadin production mode) |

Scripts in `~/model-radar-run/`:

| Script | Purpose |
| --- | --- |
| `startup.sh` | **Start here.** Brings the whole stack up in order; idempotent |
| `supervise.sh` | Restart-on-exit supervisor for the JAR; PID in `supervisor.pid`, log `model-radar.log` |
| `stop.sh` | Stops the supervisor and the JAR |
| `permalink-setup.sh <authtoken> [domain]` | One-time ngrok configuration |
| `permalink-start.sh` / `permalink-stop.sh` | Bring the public link up / down (the app keeps running) |
| `permalink-supervise.sh` | Keeps the link alive; PID in `permalink-supervisor.pid` |
| `permalink.env` | `NGROK_DOMAIN=…` (`chmod 600`) |
| `permalink-history.log` | Timestamped up/down events for the link |

---

## 2. Routine startup (after sleep, reboot, or `wsl --shutdown`)

```bash
~/model-radar-run/startup.sh
```

It performs, in order and idempotently:

1. **PostgreSQL** — starts the Compose container if it is not already running and
   waits for `pg_isready` (requires Docker Desktop to be up on Windows).
2. **Application** — if `http://localhost:8080/` does not answer, launches the
   supervisor detached (`setsid nohup`) and waits for the port.
3. **Public link** — runs `permalink-start.sh` if `permalink.env` exists.

It finishes by printing the local URL and the `/api/health` body.

**After a long sleep, `/api/health` legitimately reports `DOWN`** — the last
successful sync is simply older than 26 hours. That is staleness, not an outage.
Clear it with:

```bash
curl -X POST http://localhost:8080/api/admin/sync   # ~1 minute
curl -s http://localhost:8080/api/health            # expect UP
```

Manual equivalents, if `startup.sh` cannot be used:

```bash
cd ~/projects/intics-model-radar && docker compose up -d
setsid nohup ~/model-radar-run/supervise.sh >/dev/null 2>&1 </dev/null &
~/model-radar-run/permalink-start.sh
```

Shutdown: `~/model-radar-run/permalink-stop.sh` then `~/model-radar-run/stop.sh`.

---

## 3. The public link

**<https://reburial-imprudent-deduct.ngrok-free.dev>**

An ngrok tunnel to `localhost:8080` on a domain reserved to the INTICS ngrok account,
so it survives restarts, reboots and sleep and can be bookmarked or pasted into a
document. Configuration lives in `permalink.env` and `~/.config/ngrok/ngrok.yml`
(both `chmod 600`). To move it to another account or domain:

```bash
~/model-radar-run/permalink-setup.sh <authtoken> <domain>
~/model-radar-run/permalink-start.sh
```

**The supervisor checks the public URL from the outside every 60 seconds** and rebuilds
the tunnel after three consecutive failures. That design is deliberate: an earlier
tunnel kept its process alive while its edge registration was dead, and the link stayed
broken for 18 hours with nothing noticing. A live process proves nothing — only an
external request does. Verified by killing ngrok: the same URL returned in about 40
seconds.

### Caveats to state whenever the link is shared

- **No authentication.** Anyone with the link can read everything and change INTICS
  statuses.
- **First visit shows an ngrok interstitial** with a *Visit Site* button, and that page
  discloses the host's public IP address. One click per browser, then remembered.
  Both behaviours are ngrok free-plan features; a paid plan or a named Cloudflare
  tunnel on an INTICS-owned domain removes them.
- **The link is only up while this laptop is.** Sleep or shutdown takes it down; it
  returns on the same URL once the machine and the app are back.

### Alternatives already evaluated (2026-08-04) — do not re-litigate

| Option | Outcome |
| --- | --- |
| Cloudflare **quick** tunnel | Random `*.trycloudflare.com` hostname, assigned at registration and **impossible to reclaim** once lost. Replaced by ngrok for exactly this reason |
| Cloudflare **named** tunnel | The right long-term answer, but requires a domain INTICS controls on Cloudflare |
| localtunnel | Grants a fixed subdomain, but its interstitial publishes the host's public IP — rejected |
| serveo | Port 22 unreachable from this network |
| Tailscale Funnel | `tailscaled` needs root; no passwordless sudo on this host |

---

## 4. Housekeeping

**Application log growth.** `~/model-radar-run/model-radar.log` is append-only and the
default profile logs Hibernate SQL at `DEBUG` and bound parameters at `TRACE`; it has
reached 130 MB+ in normal use. Truncate periodically:

```bash
: > ~/model-radar-run/model-radar.log
```

The durable fix is to lower those loggers outside development — see
[Logging](../README.md#logging).

**Scheduled-sync timing on a laptop.** The daily `0 0 8 * * *` job fires late and
irregularly because WSL2 is suspended whenever Windows sleeps; the in-JVM scheduler
runs the overdue task on resume. Syncs still succeed and `/api/health` stays `UP` as
long as the machine wakes within the 26-hour window. This is inherent to in-JVM
scheduling on a laptop, not a defect — a real deployment resolves it.

**Reboot behaviour.** The supervisors survive a closed terminal but **not** a reboot or
`wsl --shutdown`. Re-run `startup.sh` afterwards.

---

## 5. WSL2 networking

If `http://localhost:8080/` hangs in the Windows browser while the app is healthy
inside WSL (root returns 200, `/api/health` responds, the port is bound), the
WSL↔Windows interop relay has broken — `cmd.exe /c echo` failing from inside WSL with
`UtilAcceptVsock … accept4 failed 110` confirms it. The same relay powers localhost
port forwarding.

Fix: run `wsl --shutdown` from a **Windows** PowerShell or CMD (it cannot be run from
inside the broken session), reopen the WSL terminal, then run `startup.sh`.

The app is not reachable over the LAN by default: WSL2 NAT means
`http://<windows-ip>:8080` requires an elevated `netsh interface portproxy` rule, and
the WSL IP changes on every `wsl --shutdown`. The public link is used instead.
