import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useState } from "react";
import { Scanner } from "@yudiel/react-qr-scanner";
import type { IDetectedBarcode } from "@yudiel/react-qr-scanner";
import {
  TicketValidationMethod,
  TicketValidationStatus,
} from "@/domain/domain";
import { AlertCircle, Check, X } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { validateTicket } from "@/lib/api";
import { useAuth } from "react-oidc-context";

const DashboardValidateQrPage: React.FC = () => {
  const { isLoading, user } = useAuth();
  const [isManual, setIsManual] = useState(false);
  const [data, setData] = useState<string | undefined>();
  const [error, setError] = useState<string | undefined>();
  const [validationStatus, setValidationStatus] = useState<
    TicketValidationStatus | undefined
  >();
  const [isValidating, setIsValidating] = useState(false);
  const [lastValidatedId, setLastValidatedId] = useState<string | undefined>();

  const handleReset = () => {
    if (isLoading || !user?.access_token) {
      return;
    }
    setIsManual(false);
    setData(undefined);
    setError(undefined);
    setValidationStatus(undefined);
    setIsValidating(false);
    setLastValidatedId(undefined);
  };

  const handleError = (err: unknown) => {
    if (err instanceof Error) {
      setError(err.message);
    } else if (typeof err === "string") {
      setError(err);
    } else {
      setError("An unknown error occurred");
    }
  };

  const handleValidate = async (id: string, method: TicketValidationMethod) => {
    if (!user?.access_token || !id.trim()) {
      setError("Missing authentication token or invalid ID");
      return;
    }

    // Prevent duplicate validations
    if (isValidating || lastValidatedId === id) {
      return;
    }

    try {
      setIsValidating(true);
      setError(undefined);
      setLastValidatedId(id);

      // FIXED: Properly handle the validation request format
      const response = await validateTicket(user.access_token, {
        id: id.trim(),
        method,
      });

      setValidationStatus(response.status);

      // Auto-reset after successful validation (except for manual mode)
      if (response.status === TicketValidationStatus.VALID && method === TicketValidationMethod.QR_SCAN) {
        setTimeout(() => {
          if (!isManual) {
            handleReset();
          }
        }, 3000);
      }
    } catch (err) {
      handleError(err);
      setValidationStatus(TicketValidationStatus.INVALID);
    } finally {
      setIsValidating(false);
    }
  };

  const handleQrScan = (result: IDetectedBarcode[]) => {
    if (result && result.length > 0) {
      const qrCodeData = result[0].rawValue;
      setData(qrCodeData);
      
      // FIXED: For QR_SCAN, we should use the QR code data directly
      // The QR code contains the QR code UUID, not the ticket UUID
      handleValidate(qrCodeData, TicketValidationMethod.QR_SCAN);
    }
  };

  const handleManualValidation = () => {
    if (data && data.trim()) {
      // FIXED: For MANUAL, we should accept the ticket UUID
      handleValidate(data.trim(), TicketValidationMethod.MANUAL);
    } else {
      setError("Please enter a valid ticket ID");
    }
  };

  const getValidationStatusDisplay = () => {
    if (isValidating) {
      return (
        <div className="absolute inset-0 flex items-center justify-center bg-blue-500/80 rounded-lg">
          <div className="text-white text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-white mx-auto mb-2"></div>
            <p className="font-semibold">Validating...</p>
          </div>
        </div>
      );
    }

    if (validationStatus === TicketValidationStatus.VALID) {
      return (
        <div className="absolute inset-0 flex items-center justify-center bg-green-500/90 rounded-lg">
          <div className="text-white text-center">
            <Check className="w-20 h-20 mx-auto mb-2" />
            <p className="font-bold text-xl">VALID TICKET</p>
          </div>
        </div>
      );
    }

    if (validationStatus === TicketValidationStatus.INVALID) {
      return (
        <div className="absolute inset-0 flex items-center justify-center bg-red-500/90 rounded-lg">
          <div className="text-white text-center">
            <X className="w-20 h-20 mx-auto mb-2" />
            <p className="font-bold text-xl">INVALID TICKET</p>
          </div>
        </div>
      );
    }

    if (validationStatus === TicketValidationStatus.EXPIRED) {
      return (
        <div className="absolute inset-0 flex items-center justify-center bg-orange-500/90 rounded-lg">
          <div className="text-white text-center">
            <AlertCircle className="w-20 h-20 mx-auto mb-2" />
            <p className="font-bold text-xl">EXPIRED TICKET</p>
          </div>
        </div>
      );
    }

    return null;
  };

  if (isLoading || !user?.access_token) {
    return (
      <div className="min-h-screen bg-black text-white flex justify-center items-center">
        <p>Loading...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-black text-white flex justify-center items-center">
      <div className="border border-gray-400 max-w-sm w-full p-4">
        {error && (
          <div className="mb-4">
            <Alert variant="destructive" className="bg-gray-900 border-red-700">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Error</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          </div>
        )}

        {/* Scanner Viewport */}
        <div className="rounded-lg overflow-hidden mx-auto mb-8 relative">
          {!isManual && (
            <Scanner
              key={`scanner-${data}-${validationStatus}-${lastValidatedId}`}
              onScan={handleQrScan}
              onError={handleError}
              allowMultiple={false}
              scanDelay={1000}
            />
          )}

          {getValidationStatusDisplay()}
        </div>

        {isManual ? (
          <div className="pb-8">
            <div className="mb-4">
              <label className="block text-sm font-medium mb-2">
                Enter Ticket ID:
              </label>
              <Input
                className="w-full text-white text-lg mb-4"
                placeholder="Enter ticket UUID for manual validation"
                value={data || ""}
                onChange={(e) => setData(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    handleManualValidation();
                  }
                }}
              />
            </div>
            <Button
              className="bg-purple-500 w-full h-[60px] hover:bg-purple-800 mb-4"
              onClick={handleManualValidation}
              disabled={isValidating || !data?.trim()}
            >
              {isValidating ? "Validating..." : "Validate Ticket"}
            </Button>
            <Button
              className="bg-gray-600 hover:bg-gray-800 w-full h-[40px] text-sm"
              onClick={() => setIsManual(false)}
            >
              Switch to QR Scanner
            </Button>
          </div>
        ) : (
          <div>
            <div className="border-white border-2 h-12 rounded-md font-mono flex justify-center items-center mb-4">
              <span className="text-center px-2 truncate">
                {data || "Scan QR Code or Enter Manual Mode"}
              </span>
            </div>
            <Button
              className="bg-gray-900 hover:bg-gray-600 border-gray-500 border-2 w-full h-[60px] text-lg mb-4"
              onClick={() => setIsManual(true)}
            >
              Switch to Manual Entry
            </Button>
          </div>
        )}

        <Button
          className="bg-gray-500 hover:bg-gray-800 w-full h-[60px] text-lg"
          onClick={handleReset}
        >
          Reset
        </Button>

        {/* Instructions */}
        <div className="mt-6 text-xs text-gray-400 space-y-1">
          <p><strong>QR Scanner:</strong> Scans QR codes from tickets</p>
          <p><strong>Manual Entry:</strong> Enter ticket UUID directly</p>
          <p>Each method uses different ID formats internally</p>
        </div>
      </div>
    </div>
  );
};

export default DashboardValidateQrPage;