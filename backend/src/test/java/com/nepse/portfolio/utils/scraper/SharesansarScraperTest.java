package com.nepse.portfolio.utils.scraper;

import com.nepse.portfolio.utils.scraper.ScrapedQuote;
import com.nepse.portfolio.utils.scraper.SharesansarScraper;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharesansarScraperTest {

    private final SharesansarScraper scraper = new SharesansarScraper();

    private static final String HTML = """
            <html><body>
            <table id="DataTables_Table_0" class="table">
              <thead>
                <tr>
                  <th>S.N.</th><th>Symbol</th><th>LTP</th><th>% Change</th><th>Open</th>
                  <th>High</th><th>Low</th><th>Volume</th><th>Prev. Close</th><th>Turnover</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>1</td><td>NICA</td><td>815.00</td><td>1.12</td><td>808.00</td>
                  <td>820.00</td><td>802.00</td><td>221,500</td><td>806.00</td><td>179,400,000</td>
                </tr>
                <tr>
                  <td>2</td><td>NBL</td><td>370.00</td><td>-1.60</td><td>375.00</td>
                  <td>379.00</td><td>367.00</td><td>143,200</td><td>376.00</td><td>53,300,000</td>
                </tr>
                <tr>
                  <td>3</td><td>BAD</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td>
                  <td>-</td><td>-</td><td>-</td>
                </tr>
              </tbody>
            </table>
            </body></html>
            """;

    @Test
    void parsesQuotesByHeaderName() {
        List<ScrapedQuote> quotes = scraper.parse(Jsoup.parse(HTML));

        assertThat(quotes).hasSize(2);
        ScrapedQuote nica = quotes.get(0);
        assertThat(nica.symbol()).isEqualTo("NICA");
        assertThat(nica.ltp()).isEqualByComparingTo("815.00");
        assertThat(nica.prevClose()).isEqualByComparingTo("806.00");
        assertThat(nica.high()).isEqualByComparingTo("820.00");
        assertThat(nica.low()).isEqualByComparingTo("802.00");
        assertThat(nica.volume()).isEqualTo(221500);
        assertThat(nica.turnover()).isEqualByComparingTo("179400000");
    }

    @Test
    void handlesNegativeChangeRows() {
        List<ScrapedQuote> quotes = scraper.parse(Jsoup.parse(HTML));

        ScrapedQuote nbl = quotes.get(1);
        assertThat(nbl.symbol()).isEqualTo("NBL");
        assertThat(nbl.changePercent()).isEqualByComparingTo("-1.60");
        assertThat(nbl.ltp()).isEqualByComparingTo("370.00");
    }

    @Test
    void returnsEmptyForUnknownLayout() {
        assertThat(scraper.parse(Jsoup.parse("<html><body>no table</body></html>"))).isEmpty();
    }
}
