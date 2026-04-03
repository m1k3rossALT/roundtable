package com.roundtable.data;

/**
 * All supported data types across all data sources.
 * Each DataSourceAdapter declares which types it supports.
 */
public enum DataType {
    PRICE,           // Current price, volume, basic market data
    FUNDAMENTALS,    // P/E, revenue, margins, debt, cash flow
    EARNINGS,        // Historical earnings, EPS, guidance
    MACRO_INDICATOR, // Interest rates, inflation, GDP, M2
    TECHNICAL,       // RSI, MACD, moving averages (Phase 2)
    NEWS             // Headlines, sentiment (Phase 2)
}
