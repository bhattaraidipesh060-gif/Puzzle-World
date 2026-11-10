// Shared error-wrapper — catches synchronous throws and passes them
// to Express's error-handling middleware for a clean JSON 500 response.
function wrap(fn) {
  return (req, res, next) => {
    try { fn(req, res, next); } catch (err) { next(err); }
  };
}
module.exports = wrap;