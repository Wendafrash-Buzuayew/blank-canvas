# Frontend image: Vite build -> static files served by nginx.
#
# This app is a CLIENT-SIDE SPA (Vite 6 + React 19 + react-router-dom), not a
# Next.js server. There is no Node process at runtime and nothing to server-
# render; `npm run build` emits static assets and nginx serves them.
#
# WHY THE VITE_* VALUES ARE BUILD ARGS AND NOT CONTAINER ENV:
#
# Vite substitutes `import.meta.env.VITE_*` at BUILD time — the values are
# literals inside the emitted JS bundle. Setting them as `env:` on the
# Deployment does nothing at all: the bundle has already been compiled, and
# the browser never sees the pod's environment. So the API base URL is fixed
# when the image is built, which means an image is environment-specific and
# must be rebuilt (not just re-deployed) to point at a different backend.
#
# The defaults below are the same-origin relative paths, which is what the
# ingress in backend/k8s/base/ingress.yaml serves: the SPA and the gateway
# share a hostname, so "/api" and "/ws" resolve to the gateway without the
# bundle hard-coding any environment's domain. Prefer keeping them.

# Pinned to an exact patch rather than the floating `22-alpine`/`22` tag: this
# is the Node/npm pair the project is developed against, so CI builds what
# developers build. Only the builder stage is affected — the runtime image
# below is nginx, so none of this reaches production.
FROM node:22.15.1-slim AS build
WORKDIR /app

# Copied first so `npm ci` is cached against dependency changes alone, not
# against every source edit.
COPY package.json package-lock.json ./

# WORKAROUND — package-lock.json is not portable, and this build fails
# without it. 222 of its 350 `resolved` URLs point at
# http://registry.safaricomet.net/, Safaricom's internal mirror, which does
# not resolve off the corporate network. `npm ci` fetches each package from
# the URL recorded in the lockfile and ignores the configured registry, so
# those 222 fail with ENOTFOUND and npm aborts with its unhelpful internal
# "Exit handler never called!" rather than naming the host it could not
# reach. On a GitHub-hosted runner every one of them fails.
#
# Rewriting the host keeps the pinned versions and the integrity hashes — the
# tarballs are the same bytes from either mirror, so the hashes still verify,
# which is what makes this safe rather than merely convenient.
#
# THE REAL FIX is to regenerate the lockfile against the public registry
# (`rm package-lock.json && npm install` on a machine whose .npmrc uses
# registry.npmjs.org) and commit it. Delete these two lines when that is
# done. See backend/k8s/README.md.
RUN sed -i 's#http://registry\.safaricomet\.net/#https://registry.npmjs.org/#g' package-lock.json

# --no-audit/--no-fund: two extra registry round trips whose output nobody
# reads in a build log.
RUN npm ci --no-audit --no-fund

COPY . .

# Same-origin defaults — see the note above before overriding these.
ARG VITE_API_BASE_URL=/api
ARG VITE_WS_URL=/ws
ARG VITE_ENABLE_PHASE_2=false
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_WS_URL=$VITE_WS_URL \
    VITE_ENABLE_PHASE_2=$VITE_ENABLE_PHASE_2

RUN npm run build


FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
