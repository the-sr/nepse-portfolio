from contextlib import contextmanager

import pandas as pd
import psycopg2

from .config import DATABASE_URL


@contextmanager
def connection():
    conn = psycopg2.connect(DATABASE_URL)
    try:
        yield conn
    finally:
        conn.close()


def load_all_prices() -> pd.DataFrame:
    query = """
        SELECT s.symbol,
               h.trade_date,
               h.open_price,
               h.high_price,
               h.low_price,
               h.close_price,
               h.volume
        FROM price_history h
        JOIN stocks s ON s.id = h.stock_id
        ORDER BY s.symbol, h.trade_date
    """
    with connection() as conn:
        return pd.read_sql_query(query, conn)


def load_symbol_prices(symbol: str, limit: int = 300) -> pd.DataFrame:
    query = """
        SELECT s.symbol,
               h.trade_date,
               h.open_price,
               h.high_price,
               h.low_price,
               h.close_price,
               h.volume
        FROM price_history h
        JOIN stocks s ON s.id = h.stock_id
        WHERE UPPER(s.symbol) = UPPER(%s)
        ORDER BY h.trade_date DESC
        LIMIT %s
    """
    with connection() as conn:
        frame = pd.read_sql_query(query, conn, params=(symbol, limit))
    return frame.sort_values("trade_date").reset_index(drop=True)
