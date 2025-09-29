import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { purchaseTicket } from "@/lib/api";
import { CheckCircle, CreditCard, Plus, Minus } from "lucide-react";
import { useEffect, useState } from "react";
import { useAuth } from "react-oidc-context";
import { useNavigate, useParams } from "react-router";

const PurchaseTicketPage: React.FC = () => {
  const { eventId, ticketTypeId } = useParams();
  const { isLoading, user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | undefined>();
  const [isPurchaseSuccess, setIsPurchaseASuccess] = useState(false);
  const [quantity, setQuantity] = useState(1);
  const [isProcessing, setIsProcessing] = useState(false);
  const [purchasedTickets, setPurchasedTickets] = useState<number>(0);
  const [quantityHint, setQuantityHint] = useState<string | undefined>();

  useEffect(() => {
    if (!isPurchaseSuccess) {
      return;
    }
    const timer = setTimeout(() => {
      navigate("/");
    }, 3000);

    return () => clearTimeout(timer);
  }, [isPurchaseSuccess, navigate]);

  const handleQuantityChange = (change: number) => {
    const newQuantity = quantity + change;
    if (newQuantity >= 1 && newQuantity <= 10) {
      setQuantity(newQuantity);
      setQuantityHint(undefined);
    } else if (newQuantity < 1) {
      setQuantityHint("Minimum 1 ticket");
    } else if (newQuantity > 10) {
      setQuantityHint("Maximum 10 tickets per purchase");
    }
  };

  const handlePurchase = async () => {
    if (isLoading || !user?.access_token || !eventId || !ticketTypeId) {
      return;
    }
    
    try {
      setIsProcessing(true);
      setError(undefined);
      
      // FIXED: Using new API that accepts quantity and returns ticket array
      const tickets = await purchaseTicket(
        user.access_token, 
        eventId, 
        ticketTypeId, 
        quantity
      );
      
      setPurchasedTickets(tickets.length);
      setIsPurchaseASuccess(true);
    } catch (err) {
      if (err instanceof Error) {
        const msg = err.message || "";
        if (msg.toLowerCase().includes("quantity")) {
          setError("Please choose 1 to 10 tickets.");
        } else if (msg.toLowerCase().includes("sold out")) {
          setError("This ticket type is sold out.");
        } else if (msg.toLowerCase().includes("forbidden") || msg.toLowerCase().includes("role")) {
          setError("You must be an attendee to purchase tickets.");
        } else {
          setError(msg);
        }
      } else if (typeof err === "string") {
        setError(err);
      } else {
        setError("An unknown error occurred");
      }
    } finally {
      setIsProcessing(false);
    }
  };

  if (isPurchaseSuccess) {
    return (
      <div className="bg-black min-h-screen text-white flex items-center">
        <div className="max-w-md mx-auto p-8 text-center">
          <div className="bg-white p-8 rounded-lg border border-gray-200 shadow-sm text-black">
            <div className="space-y-4">
              <CheckCircle className="h-16 w-16 text-green-500 mx-auto" />
              <h2 className="text-2xl font-bold text-green-600">Thank you!</h2>
              <p className="text-gray-600">
                Your ticket purchase was successful.
              </p>
              <div className="bg-green-50 p-4 rounded-lg">
                <p className="text-green-800 font-semibold">
                  {purchasedTickets} ticket{purchasedTickets > 1 ? 's' : ''} purchased
                </p>
                <p className="text-green-600 text-sm mt-1">
                  Check your dashboard to view your tickets and QR codes
                </p>
              </div>
              <p className="text-gray-600 text-sm">
                Redirecting to home page in a few seconds...
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-black min-h-screen text-white">
      <div className="max-w-md mx-auto py-20">
        <div className="bg-white border-gray-300 shadow-sm border rounded-lg space-y-6 p-6">
          <h2 className="text-2xl font-bold text-gray-800 text-center">
            Purchase Tickets
          </h2>

          {error && (
            <div className="border border-red-200 rounded-lg p-4 bg-red-50">
              <div className="text-red-500 text-sm">
                <strong>Error:</strong> {error}
              </div>
            </div>
          )}

          {/* Quantity Selection */}
          <div className="space-y-2">
            <Label className="text-gray-600">Number of Tickets</Label>
            <div className="flex items-center justify-center space-x-4">
              <Button
                type="button"
                onClick={() => handleQuantityChange(-1)}
                disabled={quantity <= 1}
                className="bg-gray-200 hover:bg-gray-300 text-gray-700 w-10 h-10 p-0"
              >
                <Minus className="h-4 w-4" />
              </Button>
              <div className="text-2xl font-bold text-gray-800 w-12 text-center">
                {quantity}
              </div>
              <Button
                type="button"
                onClick={() => handleQuantityChange(1)}
                disabled={quantity >= 10}
                className="bg-gray-200 hover:bg-gray-300 text-gray-700 w-10 h-10 p-0"
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>
            <p className="text-xs text-gray-500 text-center">
              Maximum 10 tickets per purchase
            </p>
            {quantityHint && (
              <p className="text-xs text-red-500 text-center">{quantityHint}</p>
            )}
          </div>

          {/* Mock Payment Form */}
          <div className="space-y-4 border-t pt-4">
            <h3 className="text-lg font-semibold text-gray-700">Payment Information</h3>
            
            {/* Credit Card Number */}
            <div className="space-y-2">
              <Label className="text-gray-600">Credit Card Number</Label>
              <div className="relative">
                <Input
                  type="text"
                  placeholder="1234 5678 9012 3456"
                  maxLength={19}
                  className="bg-gray-200 text-black pl-10"
                />
                <CreditCard className="absolute h-4 w-4 text-gray-400 top-2.5 left-3" />
              </div>
            </div>

            <div className="space-y-2">
              <Label className="text-gray-600">Cardholder Name</Label>
              <div className="relative">
                <Input
                  type="text"
                  placeholder="John Smith"
                  className="bg-gray-200 text-black pl-10"
                />
                <CreditCard className="absolute h-4 w-4 text-gray-400 top-2.5 left-3" />
              </div>
            </div>
          </div>

          <div className="flex justify-center pt-4">
            <Button
              className="bg-purple-500 hover:bg-purple-800 cursor-pointer w-full py-3 text-lg"
              onClick={handlePurchase}
              disabled={isProcessing}
            >
              {isProcessing 
                ? "Processing..." 
                : `Purchase ${quantity} Ticket${quantity > 1 ? 's' : ''}`
              }
            </Button>
          </div>

          <div className="text-gray-500 text-xs flex items-center justify-center border-t pt-4">
            This is a mock page, no payment details should be entered.
          </div>
        </div>
      </div>
    </div>
  );
};

export default PurchaseTicketPage;