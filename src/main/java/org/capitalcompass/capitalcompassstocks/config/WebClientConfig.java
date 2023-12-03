package org.capitalcompass.capitalcompassstocks.config;

import lombok.extern.log4j.Log4j2;
import org.capitalcompass.capitalcompassstocks.exception.PolygonClientErrorException;
import org.capitalcompass.capitalcompassstocks.exception.PolygonServerErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@Log4j2
public class WebClientConfig {

    @Value("${api.polygon.secret}")
    private String polygonSecret;

    @Value("${api.polygon.base-url}")
    private String polygonUrl;

    public static ExchangeFilterFunction errorHandler() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().is5xxServerError()) {
                return clientResponse.bodyToMono(String.class).flatMap(errorBody ->
                        Mono.error(new PolygonServerErrorException(errorBody)));
            }
            if (clientResponse.statusCode().is4xxClientError()) {
                return clientResponse.bodyToMono(String.class).flatMap(errorBody ->
                        Mono.error(new PolygonClientErrorException(errorBody)));
            }
            return Mono.just(clientResponse);
        });
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(polygonUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, polygonSecret)
                .filter(errorHandler())
                .filters(exchangeFilterFunctions -> {
                    exchangeFilterFunctions.add(logRequest());
                    exchangeFilterFunctions.add(logResponse());
                })
                .build();
    }

    ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Request: \n");
                sb.append(clientRequest.url()).append("\n");
                clientRequest
                        .headers()
                        .forEach((name, values) -> values.forEach(sb::append));
                log.debug(sb.toString());
            }
            return Mono.just(clientRequest);
        });
    }

    ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                StringBuilder sb = new StringBuilder("Response: \n");
                sb.append(clientResponse.statusCode()).append("\n");
                clientResponse
                        .headers().asHttpHeaders()
                        .forEach((name, values) -> values.forEach(sb::append));
                log.debug(sb.toString());
            }
            return Mono.just(clientResponse);
        });
    }

}
