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
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
