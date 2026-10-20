export interface User {
  id: number;
  email: string;
  name: string;
  role: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}

export interface Stock {
  id: number;
  symbol: string;
  companyName: string;
  sector: string | null;
  ltp: number;
  prevClose: number;
  openPrice: number;
  highPrice: number;
  lowPrice: number;
  volume: number;
  turnover: number;
  change: number;
  changePercent: number;
  lastUpdated: string;
  source: string;
  instrumentType: string;
  active: boolean;
}

export interface HistoryPoint {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface StockDetail {
  stock: Stock;
  history: HistoryPoint[];
}

export interface Movers {
  gainers: Stock[];
  losers: Stock[];
  mostActive: Stock[];
}

export interface SectorPerformance {
  sector: string;
  stocks: number;
  avgChangePercent: number;
  turnover: number;
}

export interface Holding {
  stockId: number;
  symbol: string;
  companyName: string;
  sector: string | null;
  quantity: number;
  averageCost: number;
  ltp: number;
  prevClose: number;
  invested: number;
  currentValue: number;
  unrealizedProfitLoss: number;
  unrealizedPercent: number;
  dayChange: number;
  dayChangePercent: number;
  realizedProfitLoss: number;
  weightPercent: number;
}

export interface PortfolioSummary {
  totalInvestment: number;
  currentValue: number;
  unrealizedProfitLoss: number;
  realizedProfitLoss: number;
  totalProfitLoss: number;
  totalProfitLossPercent: number;
  dayChange: number;
  dayChangePercent: number;
  holdingsCount: number;
}

export interface AllocationSlice {
  label: string;
  value: number;
  percent: number;
}

export interface Portfolio {
  summary: PortfolioSummary;
  holdings: Holding[];
  allocation: AllocationSlice[];
  sectorAllocation: AllocationSlice[];
}

export interface Trade {
  id: number;
  stockId: number;
  symbol: string;
  companyName: string;
  type: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  brokerage: number;
  tax: number;
  fees: number;
  total: number;
  tradeDate: string;
  notes: string | null;
}

export interface HistoricalValue {
  date: string;
  value: number;
  investment: number;
  profitLoss: number;
}

export interface Indicators {
  sma20: number | null;
  sma50: number | null;
  rsi14: number | null;
  return1d: number | null;
  return7d: number | null;
  return30d: number | null;
  volatility: number | null;
  volumeRatio: number | null;
  trend: 'BULLISH' | 'BEARISH' | 'SIDEWAYS' | string;
  support: number | null;
  resistance: number | null;
  high52Week: number | null;
  low52Week: number | null;
  averageVolume20: number | null;
  dataPoints: number;
}

export type Action = 'STRONG_BUY' | 'BUY' | 'HOLD' | 'SELL' | 'STRONG_SELL';

export interface Recommendation {
  symbol: string;
  companyName: string;
  ltp: number;
  changePercent: number;
  action: Action | string;
  score: number;
  confidence: number;
  source: 'ML' | 'RULES' | string;
  reasons: string[];
  indicators: Indicators;
  holdingQuantity: number | null;
  averageCost: number | null;
  profitLoss: number | null;
  profitLossPercent: number | null;
  generatedAt: string;
}

export interface StockAnalysis {
  stock: Stock;
  indicators: Indicators;
  recommendation: Recommendation;
}

export interface DcaOpportunity {
  symbol: string;
  companyName: string;
  quantity: number;
  averageCost: number;
  ltp: number;
  lossPercent: number;
  level: 'MINOR' | 'MAJOR' | 'AGGRESSIVE' | string;
  suggestedQuantity: number;
  suggestedBudget: number;
  newAverageCost: number;
  averageImprovementPercent: number;
  reason: string;
}

export interface SimulationResponse {
  symbol: string;
  currentQuantity: number;
  currentAverageCost: number;
  price: number;
  addedQuantity: number;
  addedCapital: number;
  newQuantity: number;
  newAverageCost: number;
  newInvestment: number;
  averageReductionPercent: number;
  feasible: boolean;
  message: string;
}

export type AlertType = 'ABOVE' | 'BELOW' | 'PERCENT_CHANGE';

export interface AlertItem {
  id: number;
  symbol: string;
  companyName: string;
  type: AlertType | string;
  threshold: number;
  active: boolean;
  triggered: boolean;
  lastTriggeredAt: string | null;
  createdAt: string;
  ltp: number | null;
  changePercent: number | null;
}

export interface AlertEvent {
  id: number;
  alertId: number;
  symbol: string;
  price: number;
  message: string;
  createdAt: string;
}

export interface Settings {
  theme: string;
  lossThresholdMinor: number;
  lossThresholdMajor: number;
  lossThresholdAggressive: number;
  baseBuyAmount: number;
  maxPositionPercent: number;
  maxCapitalPerStock: number;
  cgtShortRate: number;
  cgtLongRate: number;
}

export interface RealizedGainRow {
  symbol: string;
  companyName: string;
  quantity: number;
  buyDate: string;
  sellDate: string;
  holdingDays: number;
  costBasis: number;
  proceeds: number;
  profitLoss: number;
  taxRatePercent: number;
  estimatedTax: number;
}

export interface RealizedGainsReport {
  from: string;
  to: string;
  totalCostBasis: number;
  totalProceeds: number;
  totalProfitLoss: number;
  estimatedTax: number;
  saleCount: number;
  rows: RealizedGainRow[];
}
