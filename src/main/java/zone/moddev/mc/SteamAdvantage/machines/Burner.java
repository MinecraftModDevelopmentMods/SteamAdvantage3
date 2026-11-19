package zone.moddev.mc.SteamAdvantage.machines;

/** A processor with a fuel burn timer, shown by its screen's burn meter. */
public interface Burner {
    int burnTime();

    int totalBurnTime();
}
