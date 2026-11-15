import { Chip } from "@mui/material";

export function ActiveChip({ active }: { active: boolean }) {
  return active ? (
    <Chip label="Active" color="success" size="small" />
  ) : (
    <Chip label="Inactive" size="small" />
  );
}
