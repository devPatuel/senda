# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

# In production the SPA is served behind nginx, which proxies /api to the backend,
# so a relative base URL avoids hardcoding any host.
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime stage ----
# Unprivileged variant: nginx runs as a non-root user and listens on 8080,
# matching the backend's non-root hardening
FROM nginxinc/nginx-unprivileged:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080
