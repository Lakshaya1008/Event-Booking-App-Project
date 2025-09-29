import NavBar from "@/components/nav-bar";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AttendeesReport } from "@/domain/domain";
import { getAttendeesReport } from "@/lib/api";
import { AlertCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { useParams } from "react-router";

const DashboardAttendeesReportPage: React.FC = () => {
  const { isLoading, user } = useAuth();
  const { eventId } = useParams();

  const [data, setData] = useState<AttendeesReport | undefined>();
  const [error, setError] = useState<string | undefined>();

  useEffect(() => {
    const doFetch = async () => {
      if (!eventId || isLoading || !user?.access_token) {
        return;
      }
      try {
        setError(undefined);
        const res = await getAttendeesReport(user.access_token, eventId);
        setData(res);
      } catch (err) {
        if (err instanceof Error) setError(err.message);
        else if (typeof err === "string") setError(err);
        else setError("An unknown error occurred");
      }
    };
    doFetch();
  }, [eventId, isLoading, user?.access_token]);

  if (isLoading) {
    return <div className="min-h-screen bg-black text-white flex items-center justify-center">Loading...</div>;
  }

  return (
    <div className="min-h-screen bg-black text-white">
      <NavBar />
      <div className="container mx-auto px-4 py-8 max-w-3xl">
        <h1 className="text-2xl font-bold mb-6">Attendees Report</h1>

        {error && (
          <Alert variant="destructive" className="bg-gray-900 border-red-700 mb-6">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {data && (
          <div className="space-y-4">
            <Card className="bg-gray-900 border-gray-700">
              <CardHeader>
                <CardTitle>{data.eventName}</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="bg-gray-800 p-4 rounded">
                  <p className="text-sm text-gray-400">Total Attendees</p>
                  <p className="text-2xl font-bold">{data.totalAttendees}</p>
                </div>
              </CardContent>
            </Card>

            <Card className="bg-gray-900 border-gray-700">
              <CardHeader>
                <CardTitle>Attendees</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {data.attendees.map((a, idx) => (
                  <div key={idx} className="bg-gray-800 p-4 rounded">
                    <div className="flex justify-between">
                      <p className="font-semibold">{a.attendeeName}</p>
                      <p className="text-sm text-gray-300">{a.attendeeEmail}</p>
                    </div>
                    <div className="text-sm text-gray-300">Type: {a.ticketType} | Status: {a.ticketStatus}</div>
                    <div className="text-sm text-gray-300">Purchased: {a.purchaseDate}</div>
                    <div className="text-sm text-gray-300">Validations: {a.validationCount}</div>
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </div>
  );
};

export default DashboardAttendeesReportPage;


