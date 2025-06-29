package com.casamoreno.mercado_livre_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MercadoLivreScraper {

    private final ObjectMapper objectMapper;

    public MercadoLivreScraper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Usa Selenium para baixar o HTML completo de uma página de produto,
     * extrai o JSON interno e imprime o nome e o preço do produto no console.
     *
     * @param productUrl A URL completa do produto a ser analisado.
     */
    public void scrapeAndPrintNameAndPrice(String productUrl) {
        // 1. Limpa a URL para garantir que estamos na página principal do produto
        String cleanUrl = productUrl.split("\\?")[0].split("#")[0];
        System.out.println("INFO: Acessando a URL limpa: " + cleanUrl);

        // 2. Configura e executa o Selenium para obter o HTML completo
        String finalHtml = getFullPageHtml(cleanUrl);

        // 3. Extrai o bloco de dados JSON do HTML
        String jsonData = extractPreloadedStateJson(finalHtml);

        // 4. Analisa o JSON e imprime as informações desejadas
        printProductDetailsFromJson(jsonData);
    }

    private String getFullPageHtml(String url) {
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.get(url);

            System.out.println("INFO: Aguardando a renderização da página...");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            // Espera por um elemento que geralmente carrega por último
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("reviews_capability_v3")));
            System.out.println("SUCESSO: Página renderizada.");

            return driver.getPageSource();
        } catch (Exception e) {
            throw new RuntimeException("Falha ao executar o Selenium: " + e.getMessage(), e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private String extractPreloadedStateJson(String html) {
        Document doc = Jsoup.parse(html);
        Element scriptElement = doc.selectFirst("script:contains(window.__PRELOADED_STATE__)");

        if (scriptElement == null) {
            throw new RuntimeException("Não foi possível encontrar o script __PRELOADED_STATE__ na página.");
        }

        Pattern pattern = Pattern.compile("window\\.__PRELOADED_STATE__\\s*=\\s*(.*?);");
        Matcher matcher = pattern.matcher(scriptElement.html());

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new RuntimeException("Não foi possível extrair o conteúdo JSON do script.");
        }
    }

    private void printProductDetailsFromJson(String jsonData) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);

            // Caminhos para encontrar as informações dentro do JSON
            String title = rootNode.path("initialState").path("components").path("header").path("title").asText("Título não encontrado");
            double price = rootNode.path("initialState").path("components").path("price").path("price").path("value").asDouble();

            System.out.println("\n--- INFORMAÇÕES EXTRAÍDAS ---");
            System.out.println("Nome do Produto: " + title);
            System.out.println("Preço: " + price);
            System.out.println("-----------------------------\n");

        } catch (IOException e) {
            throw new RuntimeException("Falha ao analisar o JSON do __PRELOADED_STATE__.", e);
        }
    }
}