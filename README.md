# TCB 8112 Basket Validation SDK

This repository contains SDK implementations and integration guidance for
validating, redeeming, and rolling back 8112 Universal Coupons.

For a POS integration, the primary rule is simple: anything scanned that starts
with `8112` is sent with the completed basket to the 8112 validation service.
The service returns the applicable discount, and final redemption is performed
only after payment succeeds.

## Choose the right guide

| Requirement | Guide |
| --- | --- |
| Understand the 8112 checkout and redemption process | [8112 Universal Coupon Redemption](8112-Redemption.md) |
| Integrate a POS with the hosted webhook endpoints | [POS Webhook Integration Guide](pos_webhook/IntegrationGuide.md) |
| Use the SDK from Java | [Java Integration](java/Readme.Java.md) |
| Use the SDK from Kotlin | [Kotlin Integration](java/Readme.kotlin.md) |
| Use Java with an optional local offer database | [Java with Local Database](java/ReadmeWithLocalDatabase.java.md) |
| Use Kotlin with an optional local offer database | [Kotlin with Local Database](java/ReadmeWithLocalDatabase.kotlin.md) |
| Use the SDK from JavaScript | [JavaScript Integration](js/Readme.JavaScript.md) |
| Use JavaScript with an optional local offer database | [JavaScript with Local Database](js/ReadmeWithLocalDatabase.JavaScript.md) |
| Use the native .NET Framework 4.5 DLL | [.NET Framework 4.5 Integration](dotnet45/Readme.DotNet45.md) |

## Host the JavaScript SDK as an Express API

The following example exposes the three POS endpoints documented in the
[POS Webhook Integration Guide](pos_webhook/IntegrationGuide.md):

- `POST /coupons_valid_for_basket`
- `POST /redeem_coupons`
- `POST /rollback_coupons`

It uses an `x-api-key` header to protect the business endpoints. The TCB access
key, secret key, and access token remain inside the service and are never sent
to the POS.

### 1. Package the JavaScript SDK

Node.js 18 or newer is required.

```bash
cd js
npm pack
mkdir -p ../deployment/express
mv thecouponbureau-basket-validator-1.0.0.tgz ../deployment/express/
cd ../deployment/express
```

### 2. Create the Express application

```bash
npm init -y
npm install express ./thecouponbureau-basket-validator-1.0.0.tgz
npm pkg set scripts.start="node server.js"
```

Create `deployment/express/server.js`:

```javascript
"use strict";

const express = require("express");
const {
    fetchAccessToken,
    redeemCoupons,
    rollbackCoupons,
    validateBasketHelper,
} = require("@thecouponbureau/basket-validator");

const app = express();
const port = Number(process.env.PORT || 8080);
const tokenLifetimeMs = 23 * 60 * 60 * 1000;

let cachedToken;
let tokenCreatedAt = 0;
let tokenRequest;

app.use(express.json({ limit: "1mb" }));

function requireApiKey(request, response, next) {
    const expectedApiKey = process.env.POS_API_KEY;
    const suppliedApiKey = request.get("x-api-key");

    if (!expectedApiKey || suppliedApiKey !== expectedApiKey) {
        return response.status(401).json({
            error: {
                code: "UNAUTHORIZED",
                message: "A valid x-api-key header is required.",
            },
        });
    }

    next();
}

async function getAccessToken() {
    if (cachedToken && Date.now() - tokenCreatedAt < tokenLifetimeMs) {
        return cachedToken;
    }

    if (!tokenRequest) {
        tokenRequest = fetchAccessToken(
            process.env.TCB_BASE_URL,
            process.env.TCB_ACCESS_KEY,
            process.env.TCB_SECRET_KEY
        ).then((token) => {
            cachedToken = token;
            tokenCreatedAt = Date.now();
            return token;
        }).finally(() => {
            tokenRequest = undefined;
        });
    }

    return tokenRequest;
}

function requireCoupons(coupons) {
    if (!Array.isArray(coupons) || coupons.length === 0) {
        const error = new TypeError("coupons must be a non-empty array");
        error.statusCode = 400;
        throw error;
    }
    return coupons;
}

app.get("/health", (_request, response) => {
    response.json({ status: "ok" });
});

app.use(requireApiKey);

app.post("/coupons_valid_for_basket", async (request, response, next) => {
    try {
        const accessToken = await getAccessToken();
        const result = await validateBasketHelper({
            basket: request.body.basket,
            coupons: requireCoupons(request.body.coupons),
            tcb_base_url: process.env.TCB_BASE_URL,
            tcb_access_key: process.env.TCB_ACCESS_KEY,
            tcb_access_token: accessToken,
            mode: "retailer",
        });

        response.json({ basket_validation_output: result });
    } catch (error) {
        next(error);
    }
});

app.post("/redeem_coupons", async (request, response, next) => {
    try {
        const accessToken = await getAccessToken();
        const result = await redeemCoupons(
            process.env.TCB_BASE_URL,
            process.env.TCB_ACCESS_KEY,
            accessToken,
            requireCoupons(request.body.coupons),
            "retailer"
        );

        response.json({ redeemed_coupons: result });
    } catch (error) {
        next(error);
    }
});

app.post("/rollback_coupons", async (request, response, next) => {
    try {
        const accessToken = await getAccessToken();
        const result = await rollbackCoupons(
            process.env.TCB_BASE_URL,
            process.env.TCB_ACCESS_KEY,
            accessToken,
            requireCoupons(request.body.coupons),
            "retailer"
        );

        response.json({ rolled_back_coupons: result });
    } catch (error) {
        next(error);
    }
});

app.use((error, _request, response, _next) => {
    console.error(error);
    response.status(error.statusCode || 500).json({
        error: {
            code: error.code || "INTERNAL_ERROR",
            message: error.message || "Unexpected service error.",
        },
    });
});

app.listen(port, () => {
    console.log(`8112 webhook listening on port ${port}`);
});
```

### 3. Add environment configuration

Create `deployment/express/.env` for local testing only:

```dotenv
PORT=8080
POS_API_KEY=replace-with-a-retailer-generated-api-key
TCB_BASE_URL=https://api.try.thecouponbureau.org
TCB_ACCESS_KEY=replace-with-tcb-access-key
TCB_SECRET_KEY=replace-with-tcb-secret-key
```

Never commit `.env` or place credentials in a container image. Use the target
cloud's secret manager in production.

### 4. Add the Docker image

Create `deployment/express/Dockerfile`:

```dockerfile
FROM node:22-alpine

ENV NODE_ENV=production
WORKDIR /app

COPY package*.json ./
COPY thecouponbureau-basket-validator-1.0.0.tgz ./
RUN npm ci --omit=dev

COPY server.js ./

USER node
EXPOSE 8080
CMD ["node", "server.js"]
```

Create `deployment/express/.dockerignore`:

```text
.env
node_modules
npm-debug.log
```

Build and test locally:

```bash
docker build -t tcb-8112-webhook:1.0.0 deployment/express
docker run --rm --env-file deployment/express/.env -p 8080:8080 \
  tcb-8112-webhook:1.0.0
curl http://localhost:8080/health
```

## Deploy the container

Use one of the following cloud deployment sections. They are collapsed so the
reader can open only the applicable cloud instructions.

<details open>
<summary><strong>Google Cloud — GKE with a Helm chart</strong></summary>

### Prerequisites

- Google Cloud project with billing enabled
- `gcloud`, `kubectl`, Helm, and Docker
- permissions for Artifact Registry and GKE

Google's current GKE and Artifact Registry setup references are:

- [Create a GKE cluster](https://docs.cloud.google.com/kubernetes-engine/docs/quickstarts/create-cluster)
- [Authenticate Docker to Artifact Registry](https://docs.cloud.google.com/artifact-registry/docs/docker/authentication)
- [Install Helm](https://helm.sh/docs/intro/install/)

### 1. Create Artifact Registry and push the image

```bash
export PROJECT_ID="your-gcp-project"
export REGION="us-central1"
export REPOSITORY="tcb"
export IMAGE="$REGION-docker.pkg.dev/$PROJECT_ID/$REPOSITORY/8112-webhook:1.0.0"

gcloud config set project "$PROJECT_ID"
gcloud services enable artifactregistry.googleapis.com container.googleapis.com
gcloud artifacts repositories create "$REPOSITORY" \
  --repository-format=docker \
  --location="$REGION"
gcloud auth configure-docker "$REGION-docker.pkg.dev"

docker build -t "$IMAGE" deployment/express
docker push "$IMAGE"
```

### 2. Create a GKE cluster

```bash
gcloud container clusters create-auto tcb-8112 \
  --location="$REGION"
gcloud container clusters get-credentials tcb-8112 \
  --location="$REGION"
```

### 3. Create the Kubernetes secret

```bash
kubectl create namespace tcb
kubectl -n tcb create secret generic tcb-8112-secrets \
  --from-literal=POS_API_KEY="replace-me" \
  --from-literal=TCB_BASE_URL="https://api.try.thecouponbureau.org" \
  --from-literal=TCB_ACCESS_KEY="replace-me" \
  --from-literal=TCB_SECRET_KEY="replace-me"
```

For production, populate this secret from Google Secret Manager instead of
placing secret values in shell history.

### 4. Create the Helm chart

Create this structure:

```text
deployment/helm/tcb-8112/
├── Chart.yaml
├── values.yaml
└── templates/
    ├── deployment.yaml
    └── service.yaml
```

`Chart.yaml`:

```yaml
apiVersion: v2
name: tcb-8112
description: TCB 8112 POS webhook
type: application
version: 1.0.0
appVersion: "1.0.0"
```

`values.yaml`:

```yaml
replicaCount: 2

image:
  repository: us-central1-docker.pkg.dev/your-gcp-project/tcb/8112-webhook
  tag: 1.0.0
  pullPolicy: IfNotPresent

service:
  type: LoadBalancer
  port: 80
  targetPort: 8080
```

`templates/deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Release.Name }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: {{ .Release.Name }}
  template:
    metadata:
      labels:
        app: {{ .Release.Name }}
    spec:
      containers:
        - name: webhook
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: tcb-8112-secrets
          readinessProbe:
            httpGet:
              path: /health
              port: 8080
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
          resources:
            requests:
              cpu: 250m
              memory: 256Mi
            limits:
              cpu: 1
              memory: 512Mi
```

`templates/service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Release.Name }}
spec:
  type: {{ .Values.service.type }}
  selector:
    app: {{ .Release.Name }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: {{ .Values.service.targetPort }}
```

### 5. Deploy with Helm

```bash
helm upgrade --install tcb-8112 deployment/helm/tcb-8112 \
  --namespace tcb \
  --set image.repository="$REGION-docker.pkg.dev/$PROJECT_ID/$REPOSITORY/8112-webhook" \
  --set image.tag="1.0.0"

kubectl -n tcb rollout status deployment/tcb-8112
kubectl -n tcb get service tcb-8112
```

Configure managed TLS and a retailer-approved DNS name before allowing POS
traffic. Test `/health`, then test each protected endpoint using `x-api-key`.

</details>

<details>
<summary><strong>AWS — Amazon ECS on Fargate</strong></summary>

AWS Fargate is a serverless compute option for Amazon ECS; it does not require
or run on retailer-managed EC2 instances. Use the ECS EC2 launch type instead
if the retailer specifically requires its own EC2 capacity.

AWS references:

- [Create an ECS task for Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ECS_AWSCLI_Fargate.html)
- [Create an ECS Fargate service](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/getting-started-fargate.html)

### Prerequisites

- AWS account and configured AWS CLI
- Docker
- VPC with at least two subnets
- ECS task execution role
- Application Load Balancer and TLS certificate for production

### 1. Create ECR and push the image

```bash
export AWS_REGION="us-east-1"
export AWS_ACCOUNT_ID="123456789012"
export ECR_REPOSITORY="tcb-8112-webhook"
export IMAGE="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY:1.0.0"

aws ecr create-repository \
  --repository-name "$ECR_REPOSITORY" \
  --region "$AWS_REGION"
aws ecr get-login-password --region "$AWS_REGION" | \
  docker login --username AWS --password-stdin \
  "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

docker buildx build --platform linux/amd64 \
  -t "$IMAGE" \
  --push \
  deployment/express
```

### 2. Store production secrets

Create secrets in AWS Secrets Manager for:

- `POS_API_KEY`
- `TCB_ACCESS_KEY`
- `TCB_SECRET_KEY`

Set `TCB_BASE_URL` as a normal task environment variable. Grant the ECS task
execution role permission to read the three secret ARNs.

### 3. Create the ECS task definition

Create a Linux task definition with these settings:

| Setting | Value |
| --- | --- |
| Launch type | AWS Fargate |
| Network mode | `awsvpc` |
| CPU | `0.25 vCPU` |
| Memory | `0.5 GB` |
| Container image | ECR image created above |
| Container port | `8080` |
| Environment | `PORT=8080`, `TCB_BASE_URL=https://api.try.thecouponbureau.org` |
| Secrets | `POS_API_KEY`, `TCB_ACCESS_KEY`, `TCB_SECRET_KEY` from Secrets Manager |
| Logging | CloudWatch Logs |
| Health check | HTTP `/health` through the load balancer |

Register the task definition after configuring the task execution role and
CloudWatch log group.

### 4. Create the ECS service

1. Create an ECS cluster.
2. Create an Application Load Balancer with an HTTPS listener.
3. Create a target group using target type `ip` and health path `/health`.
4. Create an ECS service using the Fargate launch type and the task definition.
5. Select at least two subnets and a security group that accepts traffic only
   from the load balancer.
6. Attach container port `8080` to the target group.
7. Start with two tasks and enable ECS service auto scaling.
8. Point the retailer-approved DNS name to the load balancer.

### 5. Verify the deployment

```bash
curl https://coupon-api.retailer.example/health

curl -X POST https://coupon-api.retailer.example/coupons_valid_for_basket \
  -H "Content-Type: application/json" \
  -H "x-api-key: $POS_API_KEY" \
  --data @validation-request.json
```

Confirm that invalid or missing API keys return HTTP `401`, the health check is
stable across task replacements, and the service can reach the configured TCB
API over HTTPS.

</details>

## Production checklist

- Use a retailer-generated API key and rotate it through the cloud secret
  manager.
- Terminate TLS at a managed load balancer or ingress.
- Restrict network access to approved POS or retailer networks where possible.
- Keep TCB credentials out of source control, images, and application logs.
- Run at least two service instances across failure zones.
- Configure health checks, centralized logs, alerts, and request timeouts.
- Apply discounts only from successful validation responses.
- Perform final redemption only after payment succeeds.
- Call rollback only for a redeemed transaction that is later voided.
