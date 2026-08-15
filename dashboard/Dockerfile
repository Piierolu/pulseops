FROM node:24-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM dependencies AS build
WORKDIR /app
COPY . .
RUN npm run build

FROM node:24-alpine AS runtime
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV CONTROL_PLANE_INTERNAL_URL=http://control-plane:8080/api
WORKDIR /app
RUN addgroup --system --gid 1001 pulseops && adduser --system --uid 1001 pulseops
COPY --from=build --chown=pulseops:pulseops /app/public ./public
COPY --from=build --chown=pulseops:pulseops /app/.next/standalone ./
COPY --from=build --chown=pulseops:pulseops /app/.next/static ./.next/static
USER pulseops
EXPOSE 3000
CMD ["node", "server.js"]
