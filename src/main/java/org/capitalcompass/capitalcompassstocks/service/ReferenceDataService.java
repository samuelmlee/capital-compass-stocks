package org.capitalcompass.capitalcompassstocks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.capitalcompass.capitalcompassstocks.api.TickerDetailResult;
import org.capitalcompass.capitalcompassstocks.client.ReferenceDataClient;
import org.capitalcompass.capitalcompassstocks.dto.TickerDetailDTO;
import org.capitalcompass.capitalcompassstocks.dto.TickerTypesDTO;
import org.capitalcompass.capitalcompassstocks.dto.TickersDTO;
import org.capitalcompass.capitalcompassstocks.dto.TickersSearchConfigDTO;
import org.capitalcompass.capitalcompassstocks.entity.TickerDetail;
import org.capitalcompass.capitalcompassstocks.exception.TickerDetailRepositoryException;
import org.capitalcompass.capitalcompassstocks.exception.TickerNotFoundException;
import org.capitalcompass.capitalcompassstocks.repository.TickerDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;


@Service
@Log4j2
@RequiredArgsConstructor
public class ReferenceDataService {

    private final ReferenceDataClient referenceDataClient;

    private final TickerDetailRepository tickerDetailRepository;

    private final TransactionalOperator transactionalOperator;

    public Mono<TickersDTO> getTickers(TickersSearchConfigDTO config) {
        return referenceDataClient.getTickers(config).flatMap(response -> {
            String nextCursor = getCursorFromTickersResponse(response.getNextUrl());

            TickersDTO dto = TickersDTO.builder()
                    .results(response.getResults())
                    .nextCursor(nextCursor)
                    .build();
            return Mono.just(dto);
        });
    }

    public Mono<TickersDTO> getTickersByCursor(String cursor) {

        return referenceDataClient.getTickersByCursor(cursor).flatMap(response -> {
            String nextCursor = getCursorFromTickersResponse(response.getNextUrl());

            TickersDTO dto = TickersDTO.builder()
                    .results(response.getResults())
                    .nextCursor(nextCursor)
                    .build();
            return Mono.just(dto);
        });
    }

    private String getCursorFromTickersResponse(String uri) {
        try {
            MultiValueMap<String, String> parameters = UriComponentsBuilder.fromUriString(uri).build().getQueryParams();
            String cursor = parameters.getFirst("cursor");
            return cursor != null ? cursor : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public Mono<TickerDetail> getTickerDetailBySymbol(String tickerSymbol) {
        return transactionalOperator.transactional(tickerDetailRepository.findBySymbol(tickerSymbol))
                .onErrorResume(e -> {
                    log.error("Error fetching ticker detail for symbol: {}", tickerSymbol);
                    return Mono.error(new TickerDetailRepositoryException("Error accessing database for ticker symbol:" + tickerSymbol));
                });
    }

    public Mono<TickerDetail> getTickerDetailFromClient(String tickerSymbol) {
        return referenceDataClient.getTickerDetails(tickerSymbol).flatMap(response -> {
            TickerDetailResult result = response.getResults();
            if (result == null) {
                return Mono.error(new TickerNotFoundException("Not ticker detail found from client for :" + tickerSymbol));
            }
            TickerDetail tickerDetail = buildTickerDetailFromResult(response.getResults());
            return Mono.just(tickerDetail);
        });
    }

    public Mono<TickerDetailDTO> getTickerDetail(String tickerSymbol) {

        Mono<TickerDetail> tickerDetailMono = getTickerDetailBySymbol(tickerSymbol)
                .switchIfEmpty(getTickerDetailFromClient(tickerSymbol));

        return tickerDetailMono.flatMap(tickerDetail -> {
            TickerDetailDTO dto = TickerDetailDTO.builder()
                    .result(tickerDetail)
                    .build();
            return Mono.just(dto);
        });
    }

    public Mono<TickerDetail> getAndSaveTickerDetail(String tickerSymbol) {
        return getTickerDetailBySymbol(tickerSymbol)
                .switchIfEmpty(getTickerDetailFromClient(tickerSymbol).flatMap(this::saveTickerDetail));
    }


    public Mono<TickerTypesDTO> getTickerTypes() {
        return referenceDataClient.getTickerTypes().map(response -> TickerTypesDTO.builder()
                .results(response.getResults())
                .build());
    }

    public Mono<Set<String>> registerTickers(Set<String> tickerSymbols) {
        return Flux.fromIterable(tickerSymbols)
                .flatMap(this::getAndSaveTickerDetail)
                .map(TickerDetail::getSymbol)
                .collect(Collectors.toSet());
    }

    private Mono<TickerDetail> saveTickerDetail(TickerDetail detail) {
        return transactionalOperator.transactional(this.tickerDetailRepository.save(detail))
                .onErrorResume(e -> {
                    log.error("Error saving ticker detail: {}", detail, e);
                    return Mono.error(new TickerDetailRepositoryException("Error accessing database to save Ticker Detail :" + detail));
                });
    }

    private TickerDetail buildTickerDetailFromResult(TickerDetailResult result) {
        return TickerDetail.builder()
                .symbol(result.getSymbol())
                .name(result.getName())
                .market(result.getMarket())
                .primaryExchange(result.getPrimaryExchange())
                .currencyName(result.getCurrencyName())
                .type(result.getType())
                .description(result.getDescription())
                .marketCap(result.getMarketCap())
                .homePageUrl(result.getHomePageUrl())
                .totalEmployees(result.getTotalEmployees())
                .listDate(result.getListDate())
                .shareClassSharesOutstanding(result.getShareClassSharesOutstanding())
                .weightedSharesOutstanding(result.getWeightedSharesOutstanding())
                .build();
    }
}
