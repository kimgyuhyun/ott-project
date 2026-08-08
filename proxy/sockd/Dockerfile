# SOCKS5 proxy (dante sockd) for the backend's only non-HTTP outbound: SMTP submission.
#
# Why SOCKS and not the HTTP proxy: SMTP is not HTTP, so squid cannot carry it. SOCKS5
# forwards the destination HOSTNAME, so the SMTP session still does STARTTLS against
# smtp.naver.com and certificate verification keeps working unchanged. A plain TCP relay
# would break that (the client would be talking to the relay's name).
#
# Same multistage/scratch construction and the same reason as proxy/squid/Dockerfile:
# PLATFORM 2절 [절대] no package manager in the runtime image. Base pinned by digest.

FROM alpine@sha256:48b0309ca019d89d40f670aa1bc06e426dc0931948452e8491e3d65087abc07d AS build

RUN mkdir -p /out/etc/apk \
 && cp -a /etc/apk/repositories /etc/apk/keys /out/etc/apk/ \
 && apk add --no-cache --initdb --root /out --no-scripts dante-server \
 && printf 'sockd:x:1001:1001::/var/empty:/sbin/nologin\n' > /out/etc/passwd \
 && printf 'sockd:x:1001:\n' > /out/etc/group \
 && mkdir -p /out/var/run /out/var/empty \
 && chown -R 1001:1001 /out/var/run \
 && rm -rf /out/etc/apk /out/lib/apk /out/var/cache/apk \
 # Same reason as proxy/squid/Dockerfile: dante pulls busybox-binsh in transitively and
 # nothing at runtime uses a shell. sockd is a single static-ish daemon.
 && rm -rf /out/bin /out/usr/bin /out/usr/sbin/logrotate /out/etc/logrotate.d

FROM scratch
COPY --from=build /out /

# 비root 고정 UID. 1080 은 1024 이상이라 NET_BIND_SERVICE 없이 들을 수 있다.
USER 1001
EXPOSE 1080

# sockd runs in the foreground unless -D is given, so docker owns the process lifecycle.
ENTRYPOINT ["/usr/sbin/sockd", "-f", "/etc/sockd.conf"]
