(() => {
  "use strict";

  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ── Mobile nav toggle ─────────────────────────────────────────────── */
  const navToggle = document.getElementById("nav-toggle");
  const mobileMenu = document.getElementById("mobile-menu");

  function closeMobileMenu() {
    if (!navToggle || !mobileMenu) return;
    navToggle.setAttribute("aria-expanded", "false");
    mobileMenu.hidden = true;
  }

  if (navToggle && mobileMenu) {
    navToggle.addEventListener("click", () => {
      const open = navToggle.getAttribute("aria-expanded") === "true";
      navToggle.setAttribute("aria-expanded", String(!open));
      mobileMenu.hidden = open;
    });

    // Close mobile menu when any link inside it is clicked
    mobileMenu.querySelectorAll("a").forEach((link) => {
      link.addEventListener("click", closeMobileMenu);
    });

    // Fix: close mobile menu on scroll so it doesn't cover content
    window.addEventListener("scroll", () => {
      if (navToggle.getAttribute("aria-expanded") === "true") {
        closeMobileMenu();
      }
    }, { passive: true });
  }

  /* ── Progress bar: read data-width and apply dynamically ───────────── */
  // Fix: data-width attribute was unused — JS now reads it and applies
  // the correct width as an inline style when the card animates.
  document.querySelectorAll(".recovery-fill[data-width]").forEach((fill) => {
    const targetWidth = parseInt(fill.getAttribute("data-width"), 10);
    if (!isNaN(targetWidth)) {
      const card = fill.closest(".recovery-card");
      if (card) {
        // Override the hardcoded CSS 68% with the data-driven value
        const applyWidth = () => {
          fill.style.width = targetWidth + "%";
          fill.setAttribute("aria-valuenow", targetWidth);
        };
        // If already animated (e.g. after hash navigation), apply immediately
        if (card.classList.contains("animated")) {
          applyWidth();
        }
        // Observer will call applyWidth() when the card gets .animated
        const observer = new MutationObserver(() => {
          if (card.classList.contains("animated")) {
            applyWidth();
            observer.disconnect();
          }
        });
        observer.observe(card, { attributes: true, attributeFilter: ["class"] });
      }
    }
  });

  /* ── Reveal animations ─────────────────────────────────────────────── */
  const steps = document.querySelector(".steps");
  const recoveryCard = document.querySelector(".recovery-card");

  const revealEls = document.querySelectorAll(".reveal");
  if (location.hash) {
    revealEls.forEach((el) => el.classList.add("in-view"));
    if (steps) steps.classList.add("in-view");
    if (recoveryCard) recoveryCard.classList.add("animated");
  }

  if ("IntersectionObserver" in window && !reduceMotion) {
    const revealObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("in-view");
            revealObserver.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
    );
    revealEls.forEach((el) => revealObserver.observe(el));
  } else {
    revealEls.forEach((el) => el.classList.add("in-view"));
  }

  const hasObserver = "IntersectionObserver" in window && !reduceMotion;

  if (steps && hasObserver) {
    const stepsObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            steps.classList.add("in-view");
            stepsObserver.unobserve(steps);
          }
        });
      },
      { threshold: 0.3 }
    );
    stepsObserver.observe(steps);
  } else if (steps) {
    steps.classList.add("in-view");
  }

  if (recoveryCard && hasObserver) {
    const cardObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            recoveryCard.classList.add("animated");
            cardObserver.unobserve(recoveryCard);
          }
        });
      },
      { threshold: 0.3 }
    );
    cardObserver.observe(recoveryCard);
  } else if (recoveryCard) {
    recoveryCard.classList.add("animated");
  }

  /* ── FAQ accordion ─────────────────────────────────────────────────── */
  const faqItems = document.querySelectorAll(".faq-item");
  faqItems.forEach((item) => {
    // Fix: added null guards so missing .faq-q or .faq-a won't throw TypeError
    const button = item.querySelector(".faq-q");
    const answer = item.querySelector(".faq-a");
    if (!button || !answer) return;

    button.addEventListener("click", () => {
      const open = button.getAttribute("aria-expanded") === "true";
      item.classList.toggle("open", !open);
      button.setAttribute("aria-expanded", String(!open));
      answer.hidden = open;
    });
  });

  /* ── Slide scroller keyboard support ───────────────────────────────── */
  // Fix: .slide-scroller has tabindex="0" but was missing keyboard handling
  const slideScroller = document.querySelector(".slide-scroller");
  if (slideScroller) {
    slideScroller.addEventListener("keydown", (e) => {
      const scrollAmount = 280;
      if (e.key === "ArrowRight") {
        e.preventDefault();
        slideScroller.scrollBy({ left: scrollAmount, behavior: "smooth" });
      } else if (e.key === "ArrowLeft") {
        e.preventDefault();
        slideScroller.scrollBy({ left: -scrollAmount, behavior: "smooth" });
      }
    });
  }
})();
