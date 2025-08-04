    logger.info(f"Max concurrent bisects: {CONFIG['max_concurrent_bisects']}")
    logger.info(f"Predictive builds: {'enabled' if CONFIG['enable_predictive_builds'] else 'disabled'}")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        httpd.shutdown()

if __name__ == '__main__':
    main()
