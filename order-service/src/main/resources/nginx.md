user  nginx;
worker_processes  auto;
worker_rlimit_nofile 65535;

error_log  /var/log/nginx/error.log warn;
pid        /var/run/nginx.pid;

events {
worker_connections  65535;
use epoll;
multi_accept on;
}

http {
include       /etc/nginx/mime.types;
default_type  application/octet-stream;

    # 日志格式
    log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                      '$status $body_bytes_sent "$http_referer" '
                      '"$http_user_agent" "$http_x_forwarded_for" '
                      '$request_time $upstream_response_time';

    access_log  /var/log/nginx/access.log  main;

    # 性能优化
    sendfile        on;
    tcp_nopush      on;
    tcp_nodelay     on;
    keepalive_timeout  65;
    types_hash_max_size 2048;
    client_max_body_size 10M;

    # Gzip 压缩
    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml;

    # 限流配置
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=100r/s;
    limit_conn_zone $binary_remote_addr zone=conn_limit:10m;

    # Gateway 上游服务器
    upstream gateway_cluster {
        least_conn;  # 最少连接算法
        server 192.168.1.101:8080 max_fails=3 fail_timeout=30s weight=5;
        server 192.168.1.102:8080 max_fails=3 fail_timeout=30s weight=5;
        server 192.168.1.103:8080 max_fails=3 fail_timeout=30s weight=5;
        
        keepalive 32;  # 长连接池
    }

    server {
        listen 80;
        listen 443 ssl http2;
        server_name api.yourdomain.com;

        # SSL 证书
        ssl_certificate     /etc/nginx/ssl/server.crt;
        ssl_certificate_key /etc/nginx/ssl/server.key;
        ssl_protocols       TLSv1.2 TLSv1.3;
        ssl_ciphers         HIGH:!aNULL:!MD5;

        # 限流
        limit_req zone=api_limit burst=200 nodelay;
        limit_conn conn_limit 100;

        # 健康检查
        location /health {
            access_log off;
            return 200 'OK';
        }

        # 代理到 Gateway
        location / {
            proxy_pass http://gateway_cluster;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
            
            # 超时配置
            proxy_connect_timeout 10s;
            proxy_send_timeout 30s;
            proxy_read_timeout 30s;
            
            # 重试配置
            proxy_next_upstream error timeout invalid_header http_500 http_502 http_503;
            proxy_next_upstream_tries 3;
        }

        # 监控端点（仅内网访问）
        location /actuator {
            allow 192.168.1.0/24;
            deny all;
            proxy_pass http://gateway_cluster;
        }
    }
}
