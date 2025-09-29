import { useAuth } from "react-oidc-context";
import { Button } from "../components/ui/button";
import { useNavigate } from "react-router";
import { Input } from "@/components/ui/input";
import { AlertCircle, Search } from "lucide-react";
import { useEffect, useState, useCallback } from "react";
import { PublishedEventSummary, SpringBootPagination } from "@/domain/domain";
import { listPublishedEvents, searchPublishedEvents } from "@/lib/api";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import PublishedEventCard from "@/components/published-event-card";
import { SimplePagination } from "@/components/simple-pagination";

const AttendeeLandingPage: React.FC = () => {
  const { isAuthenticated, isLoading, signinRedirect, signoutRedirect, user } =
    useAuth();

  // Auto-redirect to login if not authenticated and not loading
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      signinRedirect();
    }
  }, [isLoading, isAuthenticated, signinRedirect]);

  const navigate = useNavigate();

  const [page, setPage] = useState(0);
  const [publishedEvents, setPublishedEvents] = useState<
    SpringBootPagination<PublishedEventSummary> | undefined
  >();
  const [error, setError] = useState<string | undefined>();
  const [query, setQuery] = useState<string>("");
  const [size] = useState(8);
  const [sort] = useState<string | undefined>("start,asc");
  const [isFetching, setIsFetching] = useState(false);


  const refreshPublishedEvents = useCallback(async () => {
    if (!user?.access_token) {
      setError("User is not authenticated. No access token available.");
      return;
    }
    try {
      setIsFetching(true);
      setPublishedEvents(await listPublishedEvents(user.access_token, page, size, sort));
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else if (typeof err === "string") {
        setError(err);
      } else {
        setError("An unknown error has occurred");
      }
    } finally {
      setIsFetching(false);
    }
  }, [user?.access_token, page, size, sort]);

  const queryPublishedEvents = useCallback(async () => {
    if (!query) {
      await refreshPublishedEvents();
      return;
    }

    try {
      setIsFetching(true);
      setPublishedEvents(await searchPublishedEvents(user?.access_token, query, page, size, sort));
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message);
      } else if (typeof err === "string") {
        setError(err);
      } else {
        setError("An unknown error has occurred");
      }
    } finally {
      setIsFetching(false);
    }
  }, [user?.access_token, query, page, size, sort, refreshPublishedEvents]);

  useEffect(() => {
    if (query && query.length > 0) {
      queryPublishedEvents();
    } else {
      refreshPublishedEvents();
    }
  }, [page, query, queryPublishedEvents, refreshPublishedEvents]);

  if (error) {
    return (
      <div className="min-h-screen bg-black text-white">
        <Alert variant="destructive" className="bg-gray-900 border-red-700">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Error</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
        {!isLoading && !isAuthenticated && (
          <div className="mt-4 flex justify-center">
            <Button onClick={() => signinRedirect()}>Log in</Button>
          </div>
        )}
      </div>
    );
  }

  if (isLoading || isFetching) {
    return <p>Loading...</p>;
  }

  return (
    <div className="bg-black min-h-screen text-white">
      {/* Nav */}
      <div className="flex justify-end p-4 container mx-auto">
        {isAuthenticated ? (
          <div className="flex gap-4">
            <Button
              onClick={() => navigate("/dashboard")}
              className="cursor-pointer"
            >
              Dashboard
            </Button>
            <Button
              className="cursor-pointer"
              onClick={() => signoutRedirect()}
            >
              Log out
            </Button>
          </div>
        ) : (
          <div className="flex gap-4">
            <Button className="cursor-pointer" onClick={() => signinRedirect()}>
              Log in
            </Button>
          </div>
        )}
      </div>
      {/* Hero */}
      <div className="container mx-auto px-4 mb-8">
        <div className="bg-[url(/organizers-landing-hero.png)] bg-cover min-h-[200px] rounded-lg bg-bottom md:min-h-[250px]">
          <div className="bg-black/45 min-h-[200px] md:min-h-[250px] p-15 md:p-20">
            <h1 className="text-2xl font-bold mb-4">
              Find Tickets to Your Next Event
            </h1>
            <div className="flex gap-2 max-w-lg">
              <Input
                className="bg-white text-black"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
              <Button onClick={queryPublishedEvents}>
                <Search />
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* Published Event Cards */}
      <div className="grid grid-cols-2 gap-4 px-4 md:grid-cols-4">
        {publishedEvents?.content?.map((publishedEvent) => (
          <PublishedEventCard
            publishedEvent={publishedEvent}
            key={publishedEvent.id}
          />
        ))}
      </div>
      {publishedEvents && publishedEvents.content.length === 0 && (
        <div className="text-center text-gray-400 py-10">No events found.</div>
      )}

      {publishedEvents && publishedEvents.content.length > 0 && (
        <div className="w-full flex justify-center py-8">
          <SimplePagination
            pagination={publishedEvents}
            onPageChange={setPage}
          />{" "}
        </div>
      )}
    </div>
  );
};

export default AttendeeLandingPage;
