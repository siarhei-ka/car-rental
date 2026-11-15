import { Box, type SxProps, type Theme } from "@mui/material";
import sedanComfort from "../assets/cars/sedan-comfort.svg";
import sedanEconomy from "../assets/cars/sedan-economy.svg";
import sedanPremium from "../assets/cars/sedan-premium.svg";
import suvComfort from "../assets/cars/suv-comfort.svg";
import suvEconomy from "../assets/cars/suv-economy.svg";
import suvPremium from "../assets/cars/suv-premium.svg";
import vanComfort from "../assets/cars/van-comfort.svg";
import vanEconomy from "../assets/cars/van-economy.svg";
import vanPremium from "../assets/cars/van-premium.svg";

/** One picture for each car type and class: the type gives the shape, the class the trim. */
const PICTURES: Record<string, Record<string, string>> = {
  SEDAN: { ECONOMY: sedanEconomy, COMFORT: sedanComfort, PREMIUM: sedanPremium },
  SUV: { ECONOMY: suvEconomy, COMFORT: suvComfort, PREMIUM: suvPremium },
  VAN: { ECONOMY: vanEconomy, COMFORT: vanComfort, PREMIUM: vanPremium },
};

/** A soft backdrop per class, so the classes are told apart at a glance. */
const BACKDROPS: Record<string, string> = {
  ECONOMY: "linear-gradient(160deg, #fff7ed 0%, #ffedd5 100%)",
  COMFORT: "linear-gradient(160deg, #eff6ff 0%, #dbeafe 100%)",
  PREMIUM: "linear-gradient(160deg, #f1f5f9 0%, #cbd5e1 100%)",
};

/**
 * The picture of a car type and class, without a make. A type or class added later shows the
 * sedan or the economy trim until it gets a picture of its own.
 */
export function CarPicture({
  carType,
  carClass,
  alt,
  sx,
}: {
  carType: string;
  carClass: string;
  alt: string;
  sx?: SxProps<Theme>;
}) {
  const byClass = PICTURES[carType] ?? PICTURES.SEDAN!;
  const src = byClass[carClass] ?? byClass.ECONOMY!;
  return (
    <Box
      sx={[
        {
          background: BACKDROPS[carClass] ?? BACKDROPS.ECONOMY,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          overflow: "hidden",
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      <Box
        component="img"
        src={src}
        alt={alt}
        loading="lazy"
        sx={{ width: "100%", height: "auto", display: "block", aspectRatio: "640 / 264" }}
      />
    </Box>
  );
}
