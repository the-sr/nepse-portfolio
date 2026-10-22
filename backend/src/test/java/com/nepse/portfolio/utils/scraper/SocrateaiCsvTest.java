package com.nepse.portfolio.utils.scraper;

import com.nepse.portfolio.utils.scraper.SocrateaiCsv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SocrateaiCsvTest {

    @Test
    void parsesDailyFile() {
        String csv = """
                date,close,open,high,low,volume,symbol
                2026-10-16,850.0,850.0,855.0,846.0,892,ACLBSL
                2026-10-16,339.3,350.4,350.4,330.0,232046,ADBL
                """;

        List<SocrateaiCsv.Row> rows = SocrateaiCsv.parse(csv);

        assertThat(rows).hasSize(2);
        SocrateaiCsv.Row first = rows.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(first.symbol()).isEqualTo("ACLBSL");
        assertThat(first.close()).isEqualByComparingTo(new BigDecimal("850.0"));
        assertThat(first.open()).isEqualByComparingTo(new BigDecimal("850.0"));
        assertThat(first.high()).isEqualByComparingTo(new BigDecimal("855.0"));
        assertThat(first.low()).isEqualByComparingTo(new BigDecimal("846.0"));
        assertThat(first.volume()).isEqualTo(892);
    }

    @Test
    void mapsColumnsByHeaderName() {
        String csv = """
                symbol,date,open,high,low,close,volume
                NABIL,2026-10-16,500,510,495,505,1200
                """;

        List<SocrateaiCsv.Row> rows = SocrateaiCsv.parse(csv);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).symbol()).isEqualTo("NABIL");
        assertThat(rows.get(0).close()).isEqualByComparingTo("505");
    }

    @Test
    void removesDuplicateSymbolRows() {
        String csv = """
                date,close,open,high,low,volume,symbol
                2026-03-31,1050.0,1020.0,1050.0,1002.1,4569,ACLBSL
                2026-03-31,316.0,316.0,322.0,310.3,44688,ADBL
                2026-03-31,1050.0,1020.0,1050.0,1002.1,4569,ACLBSL
                """;

        List<SocrateaiCsv.Row> rows = SocrateaiCsv.parse(csv);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SocrateaiCsv.Row::symbol).containsExactly("ACLBSL", "ADBL");
    }

    @Test
    void skipsMalformedRows() {        String csv = """
                date,close,open,high,low,volume,symbol
                2026-10-16,850.0,850.0,855.0,846.0,892,ACLBSL
                garbage
                2026-10-16,,1,2,3,4,BAD
                """;

        assertThat(SocrateaiCsv.parse(csv)).hasSize(1);
    }

    @Test
    void handlesEmptyInput() {
        assertThat(SocrateaiCsv.parse("")).isEmpty();
        assertThat(SocrateaiCsv.parse(null)).isEmpty();
        assertThat(SocrateaiCsv.parse("date,close,open,high,low,volume,symbol")).isEmpty();
    }
}
