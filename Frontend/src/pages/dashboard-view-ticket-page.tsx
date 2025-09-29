import { TicketDetails, TicketStatus } from "@/domain/domain";
import { getTicket, getTicketQr } from "@/lib/api";
import { format } from "date-fns";
import { Calendar, DollarSign, MapPin, Tag, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { useParams } from "react-router";
import { Button } from "@/components/ui/button";

const DashboardViewTicketPage: React.FC = () => {
  const [ticket, setTicket] = useState<TicketDetails | undefined>();
  const [qrCodeUrl, setQrCodeUrl] = useState<string | undefined>();
  const [isQrLoading, setIsQrCodeLoading] = useState(true);
  const [isTicketLoading, setIsTicketLoading] = useState(true);
  const [error, setError] = useState<string | undefined>();
  const [qrError, setQrError] = useState<string | undefined>();

  const { id } = useParams();
  const { isLoading, user } = useAuth();

  const fetchTicketData = async (accessToken: string, ticketId: string) => {
    try {
      setIsTicketLoading(true);
      setError(undefined);
      const ticketData = await getTicket(accessToken, ticketId);
      setTicket(ticketData);
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else if (typeof err === "string") {
        setError(err);
      } else {
        setError("Failed to fetch ticket details");
      }
    } finally {
      setIsTicketLoading(false);
    }
  };

  const fetchQrCode = async (accessToken: string, ticketId: string) => {
    try {
      setIsQrCodeLoading(true);
      setQrError(undefined);
      
      // Clean up previous QR code URL
      if (qrCodeUrl) {
        URL.revokeObjectURL(qrCodeUrl);
      }

      const qrBlob = await getTicketQr(accessToken, ticketId);
      const newQrUrl = URL.createObjectURL(qrBlob);
      setQrCodeUrl(newQrUrl);
    } catch (err) {
      if (err instanceof Error) {
        setQrError(err.message);
      } else if (typeof err === "string") {
        setQrError(err);
      } else {
        setQrError("Failed to fetch QR code");
      }
    } finally {
      setIsQrCodeLoading(false);
    }
  };

  useEffect(() => {
    if (isLoading || !user?.access_token || !id) {
      return;
    }

    const fetchData = async () => {
      await fetchTicketData(user.access_token, id);
      await fetchQrCode(user.access_token, id);
    };

    fetchData();

    // Cleanup function
    return () => {
      if (qrCodeUrl) {
        URL.revokeObjectURL(qrCodeUrl);
      }
    };
  }, [user?.access_token, isLoading, id]);

  const handleRefreshQr = async () => {
    if (!user?.access_token || !id) return;
    await fetchQrCode(user.access_token, id);
  };


  const getStatusBadgeColor = (status: TicketStatus) => {
    switch (status) {
      case TicketStatus.PURCHASED:
        return "bg-green-700 text-green-100";
      case TicketStatus.CANCELLED:
        return "bg-red-700 text-red-100";
      default:
        return "bg-gray-700 text-gray-200";
    }
  };

  if (isTicketLoading) {
    return (
      <div className="bg-black min-h-screen text-white flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-white mx-auto mb-4"></div>
          <p>Loading ticket details...</p>
        </div>
      </div>
    );
  }

  if (error || !ticket) {
    return (
      <div className="bg-black min-h-screen text-white flex items-center justify-center p-4">
        <div className="text-center">
          <p className="text-red-400 mb-4">{error || "Ticket not found"}</p>
          <Button 
            onClick={() => window.history.back()}
            className="bg-gray-700 hover:bg-gray-600"
          >
            Go Back
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-black min-h-screen text-white flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="relative bg-gradient-to-br from-purple-900 via-purple-800 to-indigo-900 rounded-3xl p-8 shadow-2xl">
          {/* Status Badge */}
          <div className="flex justify-center mb-6">
            <span
              className={`px-4 py-2 rounded-full text-sm font-medium ${getStatusBadgeColor(ticket.status)}`}
            >
              {ticket.status}
            </span>
          </div>

          {/* Event Information */}
          <div className="mb-6">
            <h1 className="text-2xl font-bold mb-2 text-center">{ticket.eventName}</h1>
            <div className="flex items-center justify-center gap-2 text-purple-200 mb-4">
              <MapPin className="w-4" />
              <span>{ticket.eventVenue}</span>
            </div>
            <div className="flex items-center justify-center gap-2 text-purple-300">
              <Calendar className="w-4 text-purple-200" />
              <div className="text-center text-sm">
                {format(new Date(ticket.eventStart), "PPP 'at' p")}
                <br />
                to {format(new Date(ticket.eventEnd), "PPP 'at' p")}
              </div>
            </div>
          </div>

          {/* QR Code Section */}
          <div className="flex justify-center mb-8">
            <div className="bg-white p-4 rounded-2xl shadow-lg">
              <div className="w-32 h-32 flex items-center justify-center">
                {isQrLoading && (
                  <div className="text-center">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-purple-600 mx-auto mb-2"></div>
                    <div className="text-gray-800 text-xs">Loading QR...</div>
                  </div>
                )}
                
                {qrError && (
                  <div className="text-center">
                    <div className="text-red-500 text-xs mb-2">⚠️</div>
                    <div className="text-red-500 text-xs mb-2">{qrError}</div>
                    <Button
                      size="sm"
                      onClick={handleRefreshQr}
                      className="bg-purple-600 hover:bg-purple-700 text-white text-xs h-6"
                    >
                      <RefreshCw className="w-3 h-3 mr-1" />
                      Retry
                    </Button>
                  </div>
                )}
                
                {qrCodeUrl && !isQrLoading && !qrError && (
                  <img
                    src={qrCodeUrl}
                    alt="QR Code for ticket entry"
                    className="w-full h-full object-contain rounded-lg"
                  />
                )}
              </div>
            </div>
          </div>

          {/* QR Instructions */}
          <div className="text-center mb-8">
            <p className="text-purple-200 text-sm">
              Present this QR code at the venue for entry
            </p>
            {qrCodeUrl && (
              <Button
                size="sm"
                onClick={handleRefreshQr}
                className="bg-purple-700/50 hover:bg-purple-600/50 text-purple-200 text-xs mt-2"
              >
                <RefreshCw className="w-3 h-3 mr-1" />
                Refresh QR Code
              </Button>
            )}
          </div>

          {/* Ticket Details */}
          <div className="space-y-4 mb-8">
            <div className="flex items-center gap-2">
              <Tag className="w-5 text-purple-200" />
              <div>
                <p className="font-semibold">Ticket Type</p>
                <p className="text-purple-200 text-sm">{ticket.description}</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <DollarSign className="w-5 text-purple-200" />
              <div>
                <p className="font-semibold">Price Paid</p>
                <p className="text-purple-200 text-sm">${ticket.price.toFixed(2)}</p>
              </div>
            </div>
          </div>

          {/* Ticket ID */}
          <div className="text-center border-t border-purple-700 pt-6">
            <h4 className="text-sm font-semibold font-mono text-purple-200">Ticket ID</h4>
            <p className="text-purple-300 text-xs font-mono break-all mt-1">{ticket.id}</p>
          </div>

          {/* Actions */}
          <div className="flex justify-center mt-6">
            <Button
              onClick={() => window.history.back()}
              className="bg-purple-700/50 hover:bg-purple-600/50 text-purple-200"
            >
              Back to Tickets
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardViewTicketPage;