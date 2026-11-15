import { useQuery } from "@tanstack/react-query";
import type { SearchParams } from "../search";
import {
  ancillaryApi,
  carTypeInfo,
  insuranceApi,
  locationInfo,
  pricingApi,
  reservationApi,
  searchApi,
  unwrap,
} from "./clients";

export function useCountries() {
  return useQuery({
    queryKey: ["countries"],
    queryFn: () => unwrap(locationInfo.GET("/api/v1/countries")),
    staleTime: 5 * 60_000,
  });
}

export function useLocations() {
  return useQuery({
    queryKey: ["locations"],
    queryFn: () => unwrap(locationInfo.GET("/api/v1/locations")),
    // By country, then city and name, so a long list reads like a directory.
    select: (locations) =>
      [...locations].sort(
        (a, b) =>
          a.country.localeCompare(b.country) ||
          a.city.localeCompare(b.city) ||
          a.name.localeCompare(b.name),
      ),
    staleTime: 5 * 60_000,
  });
}

export function useCarTypes() {
  return useQuery({
    queryKey: ["car-types"],
    queryFn: () => unwrap(carTypeInfo.GET("/api/v1/car-types")),
    staleTime: 5 * 60_000,
  });
}

export function useCarClasses() {
  return useQuery({
    queryKey: ["car-classes"],
    queryFn: () => unwrap(carTypeInfo.GET("/api/v1/car-classes")),
    staleTime: 5 * 60_000,
  });
}

export function useSearch(params: SearchParams | null) {
  return useQuery({
    queryKey: ["search", params],
    enabled: params !== null,
    queryFn: () =>
      unwrap(
        searchApi.GET("/api/v1/search", {
          params: {
            header: { "X-Country": params!.country },
            query: {
              pickUpLocationId: params!.pickUp,
              returnLocationId: params!.returnTo,
              start: params!.start,
              days: params!.days,
              carType: params!.carType,
              carClass: params!.carClass,
            },
          },
        }),
      ),
  });
}

/** The insurance options of a country with their base price per day, shown as "from" prices. */
export function useInsuranceOptions(country: string | undefined) {
  return useQuery({
    queryKey: ["insurance-options", country],
    enabled: country !== undefined,
    queryFn: () =>
      unwrap(
        insuranceApi.GET("/api/v1/insurance-options", {
          params: { header: { "X-Country": country ?? "" } },
        }),
      ),
    staleTime: 5 * 60_000,
  });
}

/** The extras on offer in a country, with their list prices. */
export function useExtras(country: string | undefined) {
  return useQuery({
    queryKey: ["extras", country],
    enabled: country !== undefined,
    queryFn: () =>
      unwrap(
        ancillaryApi.GET("/api/v1/extras", {
          params: { header: { "X-Country": country ?? "" } },
        }),
      ),
    staleTime: 5 * 60_000,
  });
}

/** The fixed fee for returning the car to another location of the country, or null if none. */
export function useOneWayFee(country: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ["one-way-fee", country],
    enabled: enabled && country !== undefined,
    queryFn: async () => {
      const { data, response } = await ancillaryApi.GET("/api/v1/one-way-fee", {
        params: { header: { "X-Country": country ?? "" } },
      });
      if (response.status === 404) return null;
      return unwrap(Promise.resolve({ data, response }));
    },
    staleTime: 5 * 60_000,
  });
}

/**
 * The final price of the chosen car with its insurance and extras, for the signed-in customer. A
 * quote reserves nothing, so it is asked again whenever the page opens.
 */
export function useQuote(params: SearchParams | null, enabled: boolean) {
  return useQuery({
    queryKey: ["quote", params],
    enabled: enabled && params !== null,
    staleTime: 0,
    gcTime: 0,
    retry: false,
    queryFn: () =>
      unwrap(
        pricingApi.POST("/api/v1/quotes", {
          params: { header: { "X-Country": params!.country } },
          body: {
            pickUpLocationId: params!.pickUp,
            returnLocationId: params!.returnTo,
            start: params!.start,
            days: params!.days,
            carType: params!.carType ?? "",
            carClass: params!.carClass ?? "",
            insurance: params!.insurance ?? "BASIC",
            extras: Object.entries(params!.extras ?? {}).map(([code, quantity]) => ({
              code,
              quantity,
            })),
          },
        }),
      ),
  });
}

/** The signed-in customer's reservations in every country, latest start first. */
export function useMyReservations(enabled: boolean) {
  return useQuery({
    queryKey: ["reservations"],
    enabled,
    queryFn: () => unwrap(reservationApi.GET("/api/v1/reservations")),
  });
}

/** One reservation of the signed-in customer. */
export function useReservation(country: string, id: string, enabled: boolean) {
  return useQuery({
    queryKey: ["reservation", country, id],
    enabled,
    queryFn: () =>
      unwrap(
        reservationApi.GET("/api/v1/reservations/{id}", {
          params: { header: { "X-Country": country }, path: { id } },
        }),
      ),
  });
}
