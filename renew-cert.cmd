@echo off
REM Renew Let's Encrypt certificates (webroot) and reload Nginx
docker run --rm -v certbot-etc:/etc/letsencrypt -v certbot-webroot:/var/www/certbot certbot/certbot renew --quiet
docker compose -f C:\solo-project\ott-project\docker-compose.yml -f C:\solo-project\ott-project\docker-compose.prod.yml exec nginx nginx -s reload


