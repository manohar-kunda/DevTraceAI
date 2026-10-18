# Live Demo Deployment Guide (FalkorDB Cloud + Render)

This guide documents how DevTrace AI is deployed to the cloud using **FalkorDB Cloud (Free Tier)** and **Render (Free Tier)** without incurring costs.

---

## 1. Prerequisites (Setup on Cloud Dashboards)

### A. FalkorDB Cloud (Database)
1. Sign up or log in at [https://app.falkordb.cloud/](https://app.falkordb.cloud/) (GitHub login supported, no credit card required).
2. Click **"Create Database"**:
   - Choose the **Free** tier (100 MB RAM, shared CPU).
   - Select cloud region (e.g. AWS us-east-1 or eu-west-1).
3. Once provisioned, navigate to your database details:
   - Note the **Host** (e.g., `xyz.cloud.falkordb.com`).
   - Note the **Port** (e.g., `12345` or standard port).
   - Generate / copy the **Password**.
   *(Note: Free tier uses standard plain Redis RESP protocol without TLS; default user is `default`).*

---

### B. Render (Hosting Backend & Static Site)
1. Sign up or log in at [https://render.com/](https://render.com/) with GitHub.
2. In the Render Dashboard, click **New +** $\rightarrow$ **Blueprint**.
3. Connect your repository: `manohar-kunda/DevTraceAI`.
4. Select the branch: `deploy` (or `main` once merged).
5. Render detects [render.yaml](file:///C:/Users/manoh/DevTraceAI/render.yaml) and provisions two services:
   - `devtrace-api`: Docker Web Service
   - `devtrace-ui`: Static Site
6. Enter the declared secret environment variables in the Render Blueprint UI:
   - For `devtrace-api`:
     - `FALKORDB_HOST`: Your FalkorDB Cloud host from Step 1A.
     - `FALKORDB_PORT`: Your FalkorDB Cloud port from Step 1A.
     - `FALKORDB_PASSWORD`: A new strong password from Step 1A.
     - `DEVTRACE_API_TOKEN`: A new random token you generate (e.g. 32-character hex).
     - `DEVTRACE_CORS_ALLOWED_ORIGIN`: The Render Static Site URL (e.g. `https://devtrace-ui.onrender.com`).
   - For `devtrace-ui`:
     - `VITE_API_BASE_URL`: The Render Backend API URL (e.g. `https://devtrace-api.onrender.com`).
7. Click **Apply**. Render will trigger the builds.

---

## 2. Finding Your URLs

Once deployed:
1. In your Render Dashboard, click `devtrace-api` to view its URL (e.g. `https://devtrace-api.onrender.com`).
2. Test health: `curl -i https://devtrace-api.onrender.com/api/health` $\rightarrow$ should return HTTP 200 with `status: UP`.
3. Click `devtrace-ui` to view the frontend application URL (e.g. `https://devtrace-ui.onrender.com`).

---

## 3. Redeployment and Re-Seeding

- **Automatic Deploys**: Pushes to the tracked branch trigger automatic rebuilds on Render.
- **Re-seeding on Startup**: The backend is configured with `DEVTRACE_SEED_ENABLED=true` and `DEVTRACE_SEED_RESET=false`. On container start or wake from sleep, it idempotently bootstraps the golden scenario (24 nodes, 26 relationships) using Cypher `MERGE` statements without duplicating entities.
- **Cold Starts**: Render's free tier spins down web services after 15 minutes of inactivity. The first incoming request triggers a cold container start which takes ~45–60 seconds.

---

## 4. Known Free Tier Limits

- **FalkorDB Cloud Free Tier**: 100 MB RAM limit, unencrypted communication over standard TCP (no TLS support on free tier), pauses after inactivity.
- **Render Free Tier**: 512 MB RAM limit, container spin-down after 15 minutes of idle time.
- **Production Guidance**: For enterprise production deployment, use FalkorDB Cloud Startup/Pro tier with TLS encryption and VPC peering, and Render Starter/Standard plans with zero-downtime persistent compute.
