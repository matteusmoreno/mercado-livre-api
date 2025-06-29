package com.casamoreno.mercado_livre_api.service;

import com.casamoreno.mercado_livre_api.domain.ProductInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
        String cleanUrl = productUrl.split("\\?")[0].split("#")[0];
        String htmlContent = getFullPageHtml(cleanUrl);

        // O extrator universal agora encontra o nó 'initialState' de qualquer tipo de página.
        JsonNode initialState = extractInitialStateData(htmlContent);

        System.out.println("INFO: Mapeando os dados do JSON para o objeto ProductInfo...");
        ProductInfo productInfo = mapInfoFromInitialState(initialState, cleanUrl);
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
            // Pausa para garantir que todos os scripts, incluindo os de hidratação da página, sejam renderizados.
            Thread.sleep(2500);
            return driver.getPageSource();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao executar o Selenium: " + e.getMessage(), e);
        } finally {
            if (driver != null) driver.quit();
        }
    }

    /**
     * Extrai o bloco de dados 'initialState' da página, tentando múltiplos padrões para garantir a compatibilidade.
     */
    private JsonNode extractInitialStateData(String html) {
        try {
            // Estratégia 1: Busca por __PRELOADED_STATE__ em uma tag <script> (Páginas /MLB-)
            // A Regex agora é flexível quanto à ordem dos atributos da tag.
            Pattern scriptTagPattern = Pattern.compile("<script\\s+[^>]*?id=\"__PRELOADED_STATE__\"[^>]*?>(.*?)</script>", Pattern.DOTALL);
            Matcher scriptTagMatcher = scriptTagPattern.matcher(html);
            if (scriptTagMatcher.find()) {
                System.out.println("INFO: JSON do tipo 'script __PRELOADED_STATE__' detectado.");
                JsonNode rootNode = objectMapper.readTree(scriptTagMatcher.group(1));
                return rootNode.path("pageState").path("initialState");
            }

            // Estratégia 2: Busca por window.__PRELOADED_STATE__ (Páginas /p/)
            Pattern windowVarPattern = Pattern.compile("window\\.__PRELOADED_STATE__\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
            Matcher windowVarMatcher = windowVarPattern.matcher(html);
            if (windowVarMatcher.find()) {
                System.out.println("INFO: JSON do tipo 'window.__PRELOADED_STATE__' detectado.");
                JsonNode rootNode = objectMapper.readTree(windowVarMatcher.group(1));
                return rootNode.path("initialState");
            }

        } catch (IOException e) {
            throw new RuntimeException("Falha ao parsear o JSON principal da página.", e);
        }

        throw new RuntimeException("Não foi possível encontrar um bloco de dados JSON compatível na página.");
    }

    /**
     * Mapeador unificado que extrai dados do nó 'initialState'.
     */
    private ProductInfo mapInfoFromInitialState(JsonNode initialState, String cleanUrl) {
        ProductInfo.ProductInfoBuilder builder = ProductInfo.builder();
        JsonNode components = initialState.path("components");

        builder.mercadoLivreId(initialState.path("id").asText(null));
        builder.mercadoLivreUrl(cleanUrl);

        JsonNode headerNode = findNode(components, "header", "short_description");
        JsonNode priceNode = findNode(components, "price", "short_description");
        JsonNode galleryNode = findNode(components, "gallery", "fixed");
        JsonNode descriptionNode = findNode(components, "description", "content_bottom");
        JsonNode stockNode = findNode(components, "stock_information", "short_description");
        JsonNode schemaData = initialState.path("schema").path(0);

        builder.productTitle(headerNode.path("title").asText(null));
        builder.fullDescription(descriptionNode.path("content").asText(null));

        String brand = schemaData.path("brand").path("name").asText(null);
        if(brand == null) brand = schemaData.path("brand").asText(null);
        builder.productBrand(brand);

        String conditionText = headerNode.path("subtitle").asText("");
        if(conditionText.toLowerCase().contains("novo")) builder.productCondition("Novo");
        else if(conditionText.toLowerCase().contains("usado")) builder.productCondition("Usado");
        else builder.productCondition(extractConditionFromSchema(schemaData));

        extractPriceInfo(builder, priceNode);
        extractGalleryInfo(builder, galleryNode);
        builder.stockStatus(stockNode.path("title").path("text").asText("Disponível"));

        return builder.build();
    }

    // --- MÉTODOS AUXILIARES ---

    private void extractPriceInfo(ProductInfo.ProductInfoBuilder builder, JsonNode priceComponent) {
        JsonNode priceDetails = priceComponent.path("price");
        if(priceDetails.isMissingNode()) priceDetails = priceComponent;

        if (!priceDetails.path("value").isMissingNode()) builder.currentPrice(BigDecimal.valueOf(priceDetails.path("value").asDouble()));
        if (!priceDetails.path("original_value").isMissingNode()) builder.originalPrice(BigDecimal.valueOf(priceDetails.path("original_value").asDouble()));

        JsonNode discountLabel = priceComponent.path("discount_label");
        if (!discountLabel.isMissingNode() && !discountLabel.path("value").isMissingNode()) {
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

    private void extractGalleryInfo(ProductInfo.ProductInfoBuilder builder, JsonNode galleryNode) {
        JsonNode picturesNode = galleryNode.path("pictures");
        String zoomTemplateUrl = galleryNode.path("picture_config").path("template_zoom").asText();
        List<String> imageUrls = new ArrayList<>();

        if (picturesNode.isArray() && !zoomTemplateUrl.isEmpty()) {
            for (JsonNode pictureNode : picturesNode) {
                String pictureId = pictureNode.path("id").asText(null);
                if (pictureId != null) {
                    String sanitizedTitle = pictureNode.path("sanitized_title").asText("");
                    String finalUrl = zoomTemplateUrl.replace("{id}", pictureId)
                            .replace("{sanitizedTitle}", sanitizedTitle);
                    imageUrls.add(finalUrl);
                }
            }
        }
        builder.galleryImageUrls(imageUrls);
    }

    private String extractConditionFromSchema(JsonNode schemaData) {
        String conditionUrl = schemaData.path("itemCondition").asText("");
        return conditionUrl.contains("NewCondition") ? "Novo" : (conditionUrl.contains("UsedCondition") ? "Usado" : null);
    }

    private JsonNode findNode(JsonNode baseNode, String primaryPath, String secondaryPath) {
        JsonNode node = baseNode.path(primaryPath);
        if (node.isMissingNode() || node.isNull()) {
            node = baseNode.path(secondaryPath);
            if(node.isArray() && node.size() > 0){
                return node.get(0);
            }
        }
        return node;
    }
}