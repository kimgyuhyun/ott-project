# Egress forward proxy (HTTP/HTTPS) for the backend's allow-listed outbound calls.
#
# Why we build this instead of using an upstream squid image:
#   PLATFORM 2절 [절대] "런타임 이미지에 빌드 도구, 패키지 매니저, 불필요한 셸 유틸리티를
#   남기지 않는다. 멀티스테이지 빌드로 산출물만 옮긴다." Every published squid image ships
#   a full distro with its package manager. Here apk installs into a staging root and the
#   final stage is FROM scratch, so the runtime image has no apk, no shell, no busybox --
#   only squid, its libraries and its error pages.
#
# Base is pinned by digest (2절 [절대] latest 금지). Dependabot moves it via the docker
# ecosystem the same way it moves backend/Dockerfile.

FROM alpine@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d AS build

# --no-scripts: the package's post-install script would adduser/addgroup inside the staging
# root, which needs a shell there. We create the account ourselves below instead.
RUN mkdir -p /out/etc/apk \
 && cp -a /etc/apk/repositories /etc/apk/keys /out/etc/apk/ \
 && apk add --no-cache --initdb --root /out --no-scripts squid \
 && printf 'squid:x:1001:1001::/var/cache/squid:/sbin/nologin\n' > /out/etc/passwd \
 && printf 'squid:x:1001:\n' > /out/etc/group \
 && mkdir -p /out/var/cache/squid /out/var/log/squid /out/var/run \
 && chown -R 1001:1001 /out/var/cache/squid /out/var/log/squid /out/var/run \
 # apk's own database and cache are build artifacts, not runtime files.
 && rm -rf /out/etc/apk /out/lib/apk /out/var/cache/apk \
 # Strip everything that is not squid itself. squid pulls busybox-binsh and logrotate in
 # transitively; neither is used at runtime (logs go to stdout/stderr, not to files that
 # need rotating) and both are exactly the toolbox an attacker wants -- this is the one
 # container on the host that still has internet.
 #   /bin           busybox + /bin/sh
 #   /usr/bin       purge, squidclient, ssl_client
 #   /usr/lib/squid auth/rewrite/store helpers, cachemgr.cgi, diskd. Not reachable from
 #                  this config: no auth ACLs, no cache_dir, no url_rewrite, and logging
 #                  is stdio: not daemon:.
 # unlinkd is the one exception and is kept: squid stats the unlinkd_program at startup
 # unconditionally, even with no cache_dir, and dies with a FATAL if it is missing
 # (measured -- the first build of this image crash-looped on exactly that).
 && find /out/usr/lib/squid -mindepth 1 ! -name unlinkd -delete \
 && rm -rf /out/bin /out/usr/bin /out/usr/sbin/logrotate /out/etc/logrotate.d

FROM scratch
COPY --from=build /out /

# 비root 고정 UID. 3128 은 1024 이상이라 NET_BIND_SERVICE 없이 들을 수 있다.
USER 1001
EXPOSE 3128

# -N: stay in the foreground (no daemonize) so docker owns the process lifecycle.
# The config is a read-only bind mount; `squid -k reconfigure` re-reads it without a restart.
ENTRYPOINT ["/usr/sbin/squid", "-N", "-f", "/etc/squid/squid.conf"]
