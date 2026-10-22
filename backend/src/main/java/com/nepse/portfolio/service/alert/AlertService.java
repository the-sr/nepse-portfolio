package com.nepse.portfolio.service.alert;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.dto.alert.AlertDtos.AlertDto;
import com.nepse.portfolio.dto.alert.AlertDtos.AlertEventDto;
import com.nepse.portfolio.dto.alert.AlertDtos.CreateAlertRequest;
import com.nepse.portfolio.dto.alert.AlertDtos.UpdateAlertRequest;

import java.util.List;

public interface AlertService {
    List<AlertDto> list(Long userId);
    AlertDto create(Long userId, CreateAlertRequest request);
    AlertDto update(Long userId, Long alertId, UpdateAlertRequest request);
    void delete(Long userId, Long alertId);
    List<AlertEventDto> events(Long userId);
    void evaluate(List<Stock> updatedStocks);
}
