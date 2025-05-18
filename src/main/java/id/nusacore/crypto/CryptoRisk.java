package id.nusacore.crypto;

public enum CryptoRisk {
    LOW,
    MEDIUM,
    HIGH,
    EXTREME;
    
    public static CryptoRisk fromString(String risk) {
        try {
            return CryptoRisk.valueOf(risk.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CryptoRisk.MEDIUM; // Default
        }
    }
    
    public String getDisplayName() {
        return switch (this) {
            case LOW -> "Rendah";
            case MEDIUM -> "Sedang";
            case HIGH -> "Tinggi";
            case EXTREME -> "Ekstrem";
        };
    }
    
    public String getColor() {
        return switch (this) {
            case LOW -> "&a"; // Green
            case MEDIUM -> "&e"; // Yellow
            case HIGH -> "&6"; // Gold
            case EXTREME -> "&c"; // Red
        };
    }
}