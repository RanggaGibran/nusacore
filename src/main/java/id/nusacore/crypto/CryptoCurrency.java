package id.nusacore.crypto;

public class CryptoCurrency {
    private final String id;
    private final String name;
    private final String symbol;
    private double currentPrice;
    private final double volatility;
    private final double minPrice;
    private final double maxPrice;
    private final CryptoRisk risk;
    
    public CryptoCurrency(String id, String name, String symbol, double initialPrice, 
                          double volatility, double minPrice, double maxPrice, CryptoRisk risk) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.currentPrice = initialPrice;
        this.volatility = volatility;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.risk = risk;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public double getCurrentPrice() {
        return currentPrice;
    }
    
    public void setCurrentPrice(double currentPrice) {
        this.currentPrice = currentPrice;
    }
    
    public double getVolatility() {
        return volatility;
    }
    
    public double getMinPrice() {
        return minPrice;
    }
    
    public double getMaxPrice() {
        return maxPrice;
    }
    
    public CryptoRisk getRisk() {
        return risk;
    }
}