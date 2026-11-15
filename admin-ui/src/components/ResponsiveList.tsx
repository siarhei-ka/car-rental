import {
  Card,
  CardContent,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import type { ReactNode } from "react";

export type Column<T> = { label: string; render: (item: T) => ReactNode };

type Props<T> = {
  items: T[];
  columns: Column<T>[];
  rowKey: (item: T) => string;
  actions?: (item: T) => ReactNode;
  empty: string;
};

/**
 * A table on wide screens, and a list of cards on phones, so that nothing needs horizontal
 * scrolling (NFR-33).
 */
export function ResponsiveList<T>({ items, columns, rowKey, actions, empty }: Props<T>) {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up("md"));

  if (items.length === 0) {
    return <Typography color="text.secondary">{empty}</Typography>;
  }
  if (!wide) {
    return (
      <Stack spacing={1.5}>
        {items.map((item) => (
          <Card key={rowKey(item)} variant="outlined">
            <CardContent>
              {columns.map((column) => (
                <Stack key={column.label} direction="row" spacing={1} sx={{ py: 0.25 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ minWidth: 110 }}>
                    {column.label}
                  </Typography>
                  <Typography variant="body2" sx={{ wordBreak: "break-word" }}>
                    {column.render(item)}
                  </Typography>
                </Stack>
              ))}
              {actions && (
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  {actions(item)}
                </Stack>
              )}
            </CardContent>
          </Card>
        ))}
      </Stack>
    );
  }
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          {columns.map((column) => (
            <TableCell key={column.label}>{column.label}</TableCell>
          ))}
          {actions && <TableCell />}
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item) => (
          <TableRow key={rowKey(item)}>
            {columns.map((column) => (
              <TableCell key={column.label}>{column.render(item)}</TableCell>
            ))}
            {actions && (
              <TableCell align="right" sx={{ whiteSpace: "nowrap" }}>
                {actions(item)}
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
