# Etapa 1: Construir o projeto com Maven
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Usamos -Dmaven.test.skip=true para pular os testes durante a build do Docker, que é uma prática comum.
RUN mvn clean package -Dmaven.test.skip=true

# Etapa 2: Preparar o ambiente de execução
FROM eclipse-temurin:21-jdk-alpine

# Instalar as dependências necessárias para o Chrome headless no Alpine Linux
RUN apk add --no-cache \
    chromium \
    chromium-chromedriver \
    nss \
    freetype \
    harfbuzz \
    ttf-freefont

WORKDIR /app

# Copiar o JAR da etapa de build
COPY --from=build /app/target/*.jar mercado-livre-api.jar

# Expõe a porta que a aplicação usa
EXPOSE 8181

# Define o caminho do chromedriver como uma variável de ambiente.
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver

# Comando para executar a aplicação, passando a variável de ambiente para a JVM
CMD ["java", "-Dscraper.chromedriver.path=${CHROMEDRIVER_PATH}", "-jar", "mercado-livre-api.jar"]