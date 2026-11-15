import { useQuery } from "@tanstack/react-query";
import { administration, unwrap } from "./clients";

const header = (country: string) => ({ header: { "X-Country": country } });

export function useLocations(country: string) {
  return useQuery({
    queryKey: ["locations", country],
    queryFn: () =>
      unwrap(administration.GET("/api/v1/admin/locations", { params: header(country) })),
    // By city, then name, so a long list reads like a directory.
    select: (locations) =>
      [...locations].sort((a, b) => a.city.localeCompare(b.city) || a.name.localeCompare(b.name)),
  });
}

export function useCarTypes() {
  return useQuery({
    queryKey: ["car-types"],
    queryFn: () => unwrap(administration.GET("/api/v1/admin/car-types")),
  });
}

export function useCarClasses() {
  return useQuery({
    queryKey: ["car-classes"],
    queryFn: () => unwrap(administration.GET("/api/v1/admin/car-classes")),
  });
}

export { header as countryHeader };
