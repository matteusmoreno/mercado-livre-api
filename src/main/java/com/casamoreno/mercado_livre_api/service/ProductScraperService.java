package com.casamoreno.mercado_livre_api.service;

import com.casamoreno.mercado_livre_api.domain.ProductInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProductScraperService {

    private final ObjectMapper objectMapper;

    public ProductScraperService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ProductInfo scrapeFullProductInfo(String productUrl) {
        // A URL já é limpa aqui, removendo parâmetros e fragmentos.
        String cleanUrl = productUrl.split("\\?")[0].split("#")[0];
        String htmlContent = getFullPageHtml(cleanUrl);
        JsonNode preloadedState = extractPreloadedState(htmlContent);

        System.out.println("INFO: Mapeando os dados do JSON para o objeto ProductInfo...");
        // A URL limpa (cleanUrl) é passada para o método de mapeamento.
        ProductInfo productInfo = mapJsonToProductInfo(preloadedState, cleanUrl);
        System.out.println("SUCESSO FINAL: Objeto ProductInfo criado com sucesso!");
        return productInfo;
    }

    private String getFullPageHtml(String url) {
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu", "--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(wd -> ((JavascriptExecutor) wd).executeScript("return window.__PRELOADED_STATE__ != null"));
            return driver.getPageSource();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao executar o Selenium: " + e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private JsonNode extractPreloadedState(String html) {
        Pattern pattern = Pattern.compile("window\\.__PRELOADED_STATE__\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            try {
                return objectMapper.readTree(matcher.group(1));
            } catch (IOException e) {
                throw new RuntimeException("Falha ao parsear o JSON do __PRELOADED_STATE__.", e);
            }
        }
        throw new RuntimeException("Não foi possível extrair o __PRELOADED_STATE__ do HTML.");
    }

    private ProductInfo mapJsonToProductInfo(JsonNode rootNode, String cleanUrl) {
        ProductInfo.ProductInfoBuilder builder = ProductInfo.builder();
        JsonNode initialState = rootNode.path("initialState");
        JsonNode components = initialState.path("components");

        extractCoreInfo(builder, initialState, components, cleanUrl);
        extractPriceInfo(builder, components);
        extractGalleryInfo(builder, components);
        extractOtherInfo(builder, components);

        return builder.build();
    }

    private void extractCoreInfo(ProductInfo.ProductInfoBuilder builder, JsonNode initialState, JsonNode components, String cleanUrl) {
        JsonNode schemaData = initialState.path("schema").path(0);

        builder.mercadoLivreId(initialState.path("id").asText(null));

        // MELHORIA APLICADA AQUI
        // Agora, usamos a 'cleanUrl' que foi tratada no início, ignorando a URL com parâmetros do JSON.
        builder.mercadoLivreUrl(cleanUrl)
                .productTitle(components.path("header").path("title").asText(null))
                .fullDescription(components.path("description").path("content").asText(null))
                .productBrand(schemaData.path("brand").asText(null))
                .productCondition(extractCondition(schemaData));
    }

    private void extractPriceInfo(ProductInfo.ProductInfoBuilder builder, JsonNode components) {
        JsonNode priceComponent = components.path("price");
        JsonNode priceNode = priceComponent.path("price");

        if (!priceNode.path("value").isMissingNode()) {
            builder.currentPrice(BigDecimal.valueOf(priceNode.path("value").asDouble()));
        }
        if (!priceNode.path("original_value").isMissingNode()) {
            builder.originalPrice(BigDecimal.valueOf(priceNode.path("original_value").asDouble()));
        }

        JsonNode discountLabel = priceComponent.path("discount_label");
        if (!discountLabel.path("value").isMissingNode()) {
            builder.discountPercentage(discountLabel.path("value").asText() + "% OFF");
        }

        JsonNode paymentActionData = priceComponent.path("action").path("track").path("melidata_event").path("event_data");
        if (!paymentActionData.isMissingNode()) {
            builder.installments(paymentActionData.path("installments_amount").asInt(0));
            if (!paymentActionData.path("installments_value_each").isMissingNode()) {
                builder.installmentValue(BigDecimal.valueOf(paymentActionData.path("installments_value_each").asDouble()));
            }
        }
    }

    private void extractGalleryInfo(ProductInfo.ProductInfoBuilder builder, JsonNode components) {
        JsonNode galleryNode = components.path("gallery");
        JsonNode picturesNode = galleryNode.path("pictures");
        String zoomTemplateUrl = galleryNode.path("picture_config").path("template_zoom").asText();

        List<String> imageUrls = new ArrayList<>();
        if (picturesNode.isArray() && !zoomTemplateUrl.isEmpty()) {
            for (JsonNode pictureNode : picturesNode) {
                String pictureId = pictureNode.path("id").asText(null);
                if (pictureId != null) {
                    String finalUrl = zoomTemplateUrl.replace("{id}", pictureId).replace("{sanitizedTitle}", "");
                    imageUrls.add(finalUrl);
                }
            }
        }
        builder.galleryImageUrls(imageUrls);
    }

    private void extractOtherInfo(ProductInfo.ProductInfoBuilder builder, JsonNode components) {
        builder.stockStatus(components.path("stock_information").path("title").path("text").asText("Indisponível"));
    }

    private String extractCondition(JsonNode schemaData) {
        String conditionUrl = schemaData.path("itemCondition").asText("");
        return conditionUrl.contains("NewCondition") ? "Novo" : (conditionUrl.contains("UsedCondition") ? "Usado" : null);
    }
}