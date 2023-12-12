package org.capitalcompass.capitalcompassstocks.service;

import lombok.RequiredArgsConstructor;
import org.capitalcompass.capitalcompassstocks.client.ReferenceDataClient;
import org.capitalcompass.capitalcompassstocks.model.TickerDetailsDTO;
import org.capitalcompass.capitalcompassstocks.model.TickerTypesResponseDTO;
import org.capitalcompass.capitalcompassstocks.model.TickersResponseDTO;
import org.capitalcompass.capitalcompassstocks.model.TickersSearchConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class ReferenceDataService {

    private final ReferenceDataClient referenceDataClient;

    public Mono<TickersResponseDTO> getTickers(TickersSearchConfig config) {
        return referenceDataClient.getTickers(config).flatMap(response -> {
            String nextCursor = getCursorFromTickersResponse(response.getNextUrl());

            TickersResponseDTO dto = TickersResponseDTO.builder()
                    .results(response.getResults())
                    .nextCursor(nextCursor)
                    .build();
            return Mono.just(dto);
        });
    }

    public Mono<TickerDetailsDTO> getTickerDetails(String tickerSymbol) {
        return referenceDataClient.getTickerDetails(tickerSymbol).flatMap(response -> {
            TickerDetailsDTO dto = TickerDetailsDTO.builder()
                    .result(response.getResults())
                    .build();
            return Mono.just(dto);
        });
    }

    public Mono<TickersResponseDTO> getTickersByCursor(String cursor) {

        return referenceDataClient.getTickersByCursor(cursor).flatMap(response -> {
            String nextCursor = getCursorFromTickersResponse(response.getNextUrl());

            TickersResponseDTO dto = TickersResponseDTO.builder()
                    .results(response.getResults())
                    .nextCursor(nextCursor)
                    .build();
            return Mono.just(dto);
        });
    }

    public Mono<TickerTypesResponseDTO> getTickerTypes() {
        return referenceDataClient.getTickerTypes().flatMap(response -> {
            TickerTypesResponseDTO dto = TickerTypesResponseDTO.builder()
                    .results(response.getResults())
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
}