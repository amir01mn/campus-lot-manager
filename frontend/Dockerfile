# syntax=docker/dockerfile:1

# ---- build: static SPA bundle ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# Build-time flag: set to "false" to hide the one-click demo sign-in buttons.
ARG VITE_SHOW_DEMO_ACCOUNTS=true
ENV VITE_SHOW_DEMO_ACCOUNTS=${VITE_SHOW_DEMO_ACCOUNTS}
RUN npm run build

# ---- runtime: unprivileged nginx serving the bundle and proxying the API ----
FROM nginxinc/nginx-unprivileged:1.31-alpine
# Where /api, /swagger-ui and /v3/api-docs are forwarded (the compose service name by default).
ENV BACKEND_URL=http://backend:8080
# The official entrypoint renders templates with envsubst (only defined variables, so $uri etc. survive).
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf nginx/proxy.conf /etc/nginx/snippets/
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -qO /dev/null http://127.0.0.1:8080/healthz || exit 1
