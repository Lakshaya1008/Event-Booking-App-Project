import NavBar from "@/components/nav-bar";
import { SimplePagination } from "@/components/simple-pagination";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { SpringBootPagination, TicketValidationResponse } from "@/domain/domain";
import { getEventValidations } from "@/lib/api";
import { AlertCircle, ScanLine } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { useParams } from "react-router";

const DashboardValidationsByEventPage: React.FC = () => {
  const { isLoading, user } = useAuth();
  const { eventId } = useParams();

  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [sort] = useState<string | undefined>("id,desc");
  const [data, setData] = useState<SpringBootPagination<TicketValidationResponse> | undefined>();
  const [error, setError] = useState<string | undefined>();

  useEffect(() => {
    const doFetch = async () => {
      if (!eventId || isLoading || !user?.access_token) return;
      try {
        setError(undefined);
        const res = await getEventValidations(user.access_token, eventId, page, size, sort);
        setData(res);
      } catch (err) {
        if (err instanceof Error) setError(err.message);
        else if (typeof err === "string") setError(err);
        else setError("An unknown error occurred");
      }
    };
    doFetch();
  }, [eventId, isLoading, user?.access_token, page, size, sort]);

  return (
    <div className="min-h-screen bg-black text-white">
      <NavBar />
      <div className="container mx-auto px-4 py-8 max-w-3xl">
        <h1 className="text-2xl font-bold mb-6">Event Validations</h1>

        {error && (
          <Alert variant="destructive" className="bg-gray-900 border-red-700 mb-6">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="space-y-2">
          {data?.content.map((v, idx) => (
            <Card key={`${v.ticketId}-${idx}`} className="bg-gray-900 border-gray-700">
              <CardHeader>
                <div className="flex justify-between items-center">
                  <CardTitle className="text-base flex items-center gap-2">
                    <ScanLine className="h-4 w-4 text-gray-400" />
                    Ticket {v.ticketId}
                  </CardTitle>
                  <span className="text-sm">{v.status}</span>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-gray-400 text-sm">Validation recorded.</p>
              </CardContent>
            </Card>
          ))}
        </div>

        {data && (
          <div className="flex justify-center py-8">
            <SimplePagination pagination={data} onPageChange={setPage} />
          </div>
        )}
      </div>
    </div>
  );
};

export default DashboardValidationsByEventPage;


