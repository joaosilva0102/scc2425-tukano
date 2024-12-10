FROM node:23-alpine
WORKDIR /app
RUN apk add --no-cache git && npm install -g artillery@latest && npm install -g node-fetch@latest -save && npm install -g https://github.com/preguica/artillery-plugin-metrics-by-endpoint.git
COPY package*.json ./
RUN npm install
COPY . /app
RUN chmod +x ./artillery-script.sh
CMD ["./artillery-script.sh"]



