package com.nepse.portfolio.service.alert;

import com.nepse.portfolio.model.Stock;
import com.nepse.portfolio.dto.AlertDtos.AlertDto;
import com.nepse.portfolio.dto.AlertDtos.AlertEventDto;
import com.nepse.portfolio.dto.AlertDtos.CreateAlertRequest;
import com.nepse.portfolio.dto.AlertDtos.UpdateAlertRequest;

import java.util.List;

public interface AlertService {
    List<AlertDto> list(Long userId);
    AlertDto create(Long userId, CreateAlertRequest request);
    AlertDto update(Long userId, Long alertId, UpdateAlertRequest request);
    void delete(Long userId, Long alertId);
    List<AlertEventDto> events(Long userId);
    void evaluate(List<Stock> updatedStocks);
}
